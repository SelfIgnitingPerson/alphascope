package com.alphascope.fundamental.provider;

import com.alphascope.common.util.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 直接访问免费公开财报端点，不需要 Python 或 Tushare Token。金额为人民币元，同比为小数。
 *
 * @author chenxuefeng
 */
public final class EastmoneyFinancialProvider implements AutoCloseable {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final ObjectMapper json =
      new ObjectMapper()
          .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  private final URI endpoint;

  public EastmoneyFinancialProvider() {
    this(URI.create("https://datacenter-web.eastmoney.com/api/data/v1/get"));
  }

  public EastmoneyFinancialProvider(URI endpoint) {
    this.endpoint = endpoint;
  }

  /**
   * 供应商公开财报的规范化累计值；保留原始响应、修订时间及保守可用时点。
   *
   * @author chenxuefeng
   */
  public record Statement(
      /** 累计财报的报告期末日期，按同报告期进行同比比较。 */
      LocalDate periodEnd,

      /** 供应商财报的累计报告类型：一季、半年、三季或全年；不与单季度值混算。 */
      String reportType,

      /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
      LocalDateTime publishedAt,

      /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
      LocalDateTime availableAt,

      /** 供应商标示的内容修订时刻，不等同于已核验的原始公告修订时间。 */
      LocalDateTime vendorUpdatedAt,

      /** 累计营业收入，单位人民币元；为空表示尚无可用证据。 */
      BigDecimal revenue,

      /** 累计归属于母公司股东的净利润，单位人民币元；允许亏损，空值表示缺失。 */
      BigDecimal netProfit,

      /** 累计经营活动现金流量净额，单位人民币元；需与历年相同累计期间比较。 */
      BigDecimal operatingCashflow,

      /** 累计毛利率，以小数存储；例如0.30表示30%，空值表示缺失。 */
      BigDecimal grossMargin,

      /** 累计营业收入同比增速，以小数存储；例如0.20表示20%。 */
      BigDecimal revenueYoy,

      /** 累计归母净利润同比增速，以小数存储；例如-0.10表示下降10%。 */
      BigDecimal profitYoy,

      /** 可核验的公开原始来源URL。 */
      String sourceUrl,

      /** 供应商原始响应JSON，保留当次证据，不能作为系统指令。 */
      String rawJson,

      /** 本次原始响应内容的SHA-256指纹。 */
      String hash) {}

  public List<Statement> fetch(String symbol) {
    if (!symbol.matches("[0-9]{6}\\.(SZ|SH|BJ)")) {
      throw new IllegalArgumentException("财报证券代码无效");
    }
    try {
      var income = request("RPT_F10_FINANCE_GINCOME", symbol);
      var cashflow = request("RPT_F10_FINANCE_GCASHFLOW", symbol);
      JsonNode balance = json.createArrayNode();
      try {
        balance = request("RPT_F10_FINANCE_GBALANCE", symbol);
      } catch (IOException unavailable) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("资产负债表暂不可用 symbol={} reason={}", symbol, unavailable.getClass().getSimpleName());
      }
      return parse(
          symbol,
          income,
          cashflow,
          balance,
          LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
          endpoint.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("FINANCIAL_FETCH_INTERRUPTED", e);
    } catch (IOException e) {
      throw new IllegalStateException("FINANCIAL_PROVIDER_UNAVAILABLE", e);
    }
  }

  private JsonNode request(String report, String symbol) throws IOException, InterruptedException {
    String query =
        "reportName="
            + report
            + "&columns=ALL&pageSize=20&pageNumber=1&sortColumns=REPORT_DATE&sortTypes=-1&filter="
            + URLEncoder.encode("(SECUCODE=\"" + symbol + "\")", StandardCharsets.UTF_8);
    var request =
        HttpRequest.newBuilder(URI.create(endpoint + "?" + query))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "AlphaScope/0.1 research")
            .GET()
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200 || response.body().length() > 5_000_000) {
      throw new IOException("INVALID_FINANCIAL_RESPONSE");
    }
    var root = json.readTree(response.body());
    if (!root.path("success").asBoolean() || !root.path("result").path("data").isArray()) {
      throw new IOException("INVALID_FINANCIAL_DATASET");
    }
    return root.path("result").path("data");
  }

  /** 只接受相同证券、同累计报告期的合并报表；不能把季度与累计数据混在同比中。 */
  public static List<Statement> parse(
      String symbol, JsonNode income, JsonNode cashflow, LocalDateTime fetchedAt, String endpoint) {
    return parse(
        symbol, income, cashflow, new ObjectMapper().createArrayNode(), fetchedAt, endpoint);
  }

  /**
   * 匹配三张同源同报告期报表，新增报表缺失保留未知，修订时间取最晚证据。
   *
   * @param symbol 统一证券代码
   * @param income 利润表
   * @param cashflow 现金流表
   * @param balance 资产负债表
   * @param fetchedAt 实际采集时间
   * @param endpoint 实际来源入口
   * @return 有三表原文和保守可得时点的版本
   */
  public static List<Statement> parse(
      String symbol,
      JsonNode income,
      JsonNode cashflow,
      JsonNode balance,
      LocalDateTime fetchedAt,
      String endpoint) {
    Map<LocalDate, JsonNode> balances = new HashMap<>();
    for (var row : balance)
      if (symbol.equals(row.path("SECUCODE").asText())) {
        var date = day(row, "REPORT_DATE");
        if (date != null
            && type(date, row.path("REPORT_TYPE").asText()) != null
            && balances.putIfAbsent(date, row) != null)
          throw new IllegalArgumentException("DUPLICATE_BALANCE_PERIOD");
      }
    Map<LocalDate, JsonNode> cash = new HashMap<>();
    for (var row : cashflow) {
      if (symbol.equals(row.path("SECUCODE").asText())) {
        LocalDate p = day(row, "REPORT_DATE");
        if (p != null
            && type(p, row.path("REPORT_TYPE").asText()) != null
            && cash.putIfAbsent(p, row) != null) {
          throw new IllegalArgumentException("DUPLICATE_CASHFLOW_PERIOD");
        }
      }
    }
    List<Statement> out = new ArrayList<>();
    Set<LocalDate> seen = new HashSet<>();
    for (var row : income) {
      if (!symbol.equals(row.path("SECUCODE").asText())) {
        continue;
      }
      var period = day(row, "REPORT_DATE");
      var notice = day(row, "NOTICE_DATE");
      String reportType = period == null ? null : type(period, row.path("REPORT_TYPE").asText());
      if (reportType == null || notice == null || notice.isBefore(period)) {
        continue;
      }
      if (!seen.add(period)) {
        throw new IllegalArgumentException("DUPLICATE_INCOME_PERIOD");
      }
      var c = cash.get(period);
      BigDecimal revenue = decimal(row, "OPERATE_INCOME");
      if (revenue == null) {
        revenue = decimal(row, "TOTAL_OPERATE_INCOME");
      }
      BigDecimal cost = decimal(row, "OPERATE_COST");
      BigDecimal margin =
          revenue == null || revenue.signum() <= 0 || cost == null
              ? null
              : revenue.subtract(cost).divide(revenue, 12, RoundingMode.HALF_UP);
      LocalDate update = day(row, "UPDATE_DATE");
      LocalDate latest = update == null || update.isBefore(notice) ? notice : update;
      boolean revisionKnown = update != null;
      if (c != null) {
        var cn = day(c, "NOTICE_DATE");
        var cu = day(c, "UPDATE_DATE");
        if (cn == null || cn.isBefore(period)) {
          c = null;
        } else {
          if (cn.isAfter(latest)) {
            latest = cn;
          }
          if (cu != null && cu.isAfter(latest)) {
            latest = cu;
          }
          revisionKnown &= cu != null;
        }
      }
      var b = balances.get(period);
      if (b != null) {
        var bn = day(b, "NOTICE_DATE");
        var bu = day(b, "UPDATE_DATE");
        if (bn == null || bn.isBefore(period)) b = null;
        else {
          if (bn.isAfter(latest)) latest = bn;
          if (bu != null && bu.isAfter(latest)) latest = bu;
          revisionKnown &= bu != null;
        }
      }
      // 日期精度不能假设开盘前发布；无修订日期时，仅从本次抓取时间起可用。
      LocalDateTime available = revisionKnown ? latest.plusDays(1).atStartOfDay() : fetchedAt;
      String raw =
          Jsons.write(
              b == null
                  ? Map.of("income", row, "cashflow", c == null ? NullNode.instance : c)
                  : Map.of(
                      "income", row, "cashflow", c == null ? NullNode.instance : c, "balance", b));
      String url =
          endpoint
              + "?reportName=RPT_F10_FINANCE_GINCOME&columns=ALL&filter="
              + URLEncoder.encode("(SECUCODE=\"" + symbol + "\")", StandardCharsets.UTF_8);
      out.add(
          new Statement(
              period,
              reportType,
              notice.atStartOfDay(),
              available,
              latest.atStartOfDay(),
              revenue,
              decimal(row, "PARENT_NETPROFIT"),
              c == null ? null : decimal(c, "NETCASH_OPERATE"),
              margin,
              percent(row, "OPERATE_INCOME_YOY"),
              percent(row, "PARENT_NETPROFIT_YOY"),
              url,
              raw,
              Jsons.hash(raw)));
    }
    out.sort(Comparator.comparing(Statement::periodEnd).reversed());
    return out;
  }

  private static String type(LocalDate d, String name) {
    return switch (name) {
      case "一季报" -> d.getMonthValue() == 3 && d.getDayOfMonth() == 31 ? "Q1_CUMULATIVE" : null;
      case "中报" -> d.getMonthValue() == 6 && d.getDayOfMonth() == 30 ? "H1_CUMULATIVE" : null;
      case "三季报" -> d.getMonthValue() == 9 && d.getDayOfMonth() == 30 ? "Q3_CUMULATIVE" : null;
      case "年报" -> d.getMonthValue() == 12 && d.getDayOfMonth() == 31 ? "ANNUAL" : null;
      default -> null;
    };
  }

  private static LocalDate day(JsonNode row, String key) {
    try {
      return LocalDate.parse(row.path(key).asText().substring(0, 10));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static BigDecimal decimal(JsonNode row, String key) {
    var value = row.path(key);
    if (value.isNull() || value.isMissingNode()) {
      return null;
    }
    try {
      return new BigDecimal(value.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BigDecimal percent(JsonNode row, String key) {
    var value = decimal(row, key);
    return value == null ? null : value.movePointLeft(2);
  }

  @Override
  public void close() {
    http.close();
  }
}
