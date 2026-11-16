package com.alphascope.market.provider.research;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.MarketBreadthCalculator;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.dto.TushareRequest;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.alphascope.market.provider.tushare.TushareClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tushare研究能力适配：使用同日历史名册、停牌核验和单位转换；权限不足保持不可用。
 *
 * @author chenxuefeng
 */
public class TushareResearchProvider implements ResearchDataProvider {
  private final TushareClient client;

  public TushareResearchProvider(TushareClient client) {
    this.client = client;
  }

  /** {@inheritDoc} */
  @Override
  public String code() {
    return "TUSHARE";
  }

  /** {@inheritDoc} */
  @Override
  public Set<MarketCapability> capabilities() {
    return Set.of(MarketCapability.MARKET_SNAPSHOT, MarketCapability.ADJUSTMENT_FACTORS);
  }

  /** {@inheritDoc} */
  @Override
  public ResearchMarketSnapshot snapshot(LocalDate date) {
    String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
    if (date.isBefore(LocalDate.of(2016, 1, 1))) {
      throw new ProviderDataUnavailableException("TUSHARE_HISTORICAL_UNIVERSE_UNAVAILABLE");
    }
    List<Map<String, String>> basic = pages("bak_basic", Map.of("trade_date", day));
    if (basic.isEmpty()) {
      throw new ProviderDataUnavailableException("TUSHARE_HISTORICAL_UNIVERSE_EMPTY");
    }
    var daily = pages("daily", Map.of("trade_date", day));
    var suspended = pages("suspend_d", Map.of("trade_date", day, "suspend_type", "S"));
    return normalize(date, basic, daily, suspended);
  }

  /**
   * 按官方字段标准化截面，不根据行情缺席推断停牌；原始清单及响应共同保留。
   *
   * @param date 业务日期
   * @param input 原始基础清单、日线和停牌响应
   * @return 统一列协议的同源截面
   */
  public static ResearchMarketSnapshot normalize(LocalDate date, SnapshotRows input) {
    return normalize(date, input.basic(), input.daily(), input.suspended());
  }

  private static ResearchMarketSnapshot normalize(
      LocalDate date,
      List<Map<String, String>> basic,
      List<Map<String, String>> daily,
      List<Map<String, String>> suspended) {
    Set<String> stopped = new HashSet<>();
    for (var row : suspended) {
      // 日内停牌不视为整日停牌；日线存在时仍按交易记录计算。
      if ("S".equals(row.get("suspend_type")) && row.getOrDefault("suspend_timing", "").isBlank()) {
        stopped.add(row.get("ts_code"));
      }
    }
    Set<String> traded = new HashSet<>();
    for (var row : daily) {
      traded.add(row.get("ts_code"));
    }
    List<Map<String, String>> universe = new ArrayList<>();
    for (var row : basic) {
      String code = internal(row.get("ts_code"));
      if (!MarketBreadthCalculator.stock(code)) {
        continue;
      }
      if (!date.equals(parse(row.get("trade_date")))) {
        throw new IllegalStateException("TUSHARE_UNIVERSE_DATE_MISMATCH");
      }
      String listed = row.get("list_date"), delisted = row.get("delist_date");
      if (listed == null) {
        throw new IllegalStateException("TUSHARE_LIST_DATE_MISSING");
      }
      if (parse(listed).isAfter(date)
          || delisted != null && !delisted.isBlank() && !parse(delisted).isAfter(date)) {
        continue;
      }
      universe.add(
          Map.of(
              "code",
              code,
              "tradeStatus",
              stopped.contains(row.get("ts_code")) && !traded.contains(row.get("ts_code"))
                  ? "0"
                  : "1"));
    }
    List<Map<String, String>> normalized = new ArrayList<>();
    for (var row : daily) {
      String code = internal(row.get("ts_code"));
      if (!MarketBreadthCalculator.stock(code)) {
        continue;
      }
      normalized.add(
          Map.of(
              "code",
              code,
              "date",
              parse(row.get("trade_date")).toString(),
              "tradestatus",
              "1",
              "adjustflag",
              "3",
              "close",
              row.get("close"),
              "preclose",
              row.get("pre_close"),
              "amount",
              new BigDecimal(row.get("amount"))
                  .multiply(BigDecimal.valueOf(1000))
                  .toPlainString()));
    }
    return ResearchMarketSnapshot.builder()
        .source("TUSHARE")
        .date(date)
        .universe(universe)
        .daily(normalized)
        .upstreamJson(Jsons.write(Map.of("basic", basic, "daily", daily, "suspended", suspended)))
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public ResearchFactorWindow factors(ResearchFactorRequest request) {
    var rows =
        pages(
            "adj_factor",
            Map.of(
                "ts_code",
                external(request.symbol()),
                "start_date",
                request.start().format(DateTimeFormatter.BASIC_ISO_DATE),
                "end_date",
                request.end().format(DateTimeFormatter.BASIC_ISO_DATE)));
    if (rows.isEmpty()) {
      throw new ProviderDataUnavailableException("TUSHARE_FACTOR_MISSING");
    }
    var events = new TreeMap<LocalDate, Map<String, String>>();
    for (var row : rows) {
      LocalDate date = parse(row.get("trade_date"));
      if (!external(request.symbol()).equals(row.get("ts_code"))
          || date.isBefore(request.start())
          || date.isAfter(request.end())
          || new BigDecimal(row.get("adj_factor")).signum() <= 0) {
        throw new IllegalStateException("TUSHARE_FACTOR_SCOPE_INVALID");
      }
      if (events.put(
              date,
              Map.of(
                  "dividOperateDate", date.toString(), "backAdjustFactor", row.get("adj_factor")))
          != null) {
        throw new IllegalStateException("TUSHARE_FACTOR_DUPLICATE");
      }
    }
    // 不能把第一条因子之前的日期当作1；范围起点使用实际第一条已确认日期。
    return new ResearchFactorWindow(
        code(), events.firstKey(), request.end(), Jsons.write(events.values()), Jsons.write(rows));
  }

  private List<Map<String, String>> pages(String api, Map<String, String> parameters) {
    List<Map<String, String>> result = new ArrayList<>();
    Set<String> fingerprints = new HashSet<>();
    int limit = "bak_basic".equals(api) ? 7000 : "daily".equals(api) ? 6000 : 5000;
    for (int offset = 0; offset < 100000; offset += limit) {
      ExecutionContext.checkpoint();
      Map<String, Object> page = new TreeMap<>(parameters);
      page.put("limit", limit);
      page.put("offset", offset);
      var rows = client.query(TushareRequest.builder().api(api).parameters(page).build());
      if (!rows.isEmpty() && !fingerprints.add(Jsons.hash(Jsons.write(rows)))) {
        throw new IllegalStateException("TUSHARE_PAGINATION_UNSUPPORTED");
      }
      result.addAll(rows);
      if (rows.size() < limit) {
        return result;
      }
    }
    throw new IllegalStateException("TUSHARE_RESPONSE_TRUNCATED");
  }

  private static LocalDate parse(String value) {
    return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
  }

  private static String internal(String value) {
    if (value == null || !value.matches("\\d{6}\\.(SH|SZ)")) {
      return "";
    }
    return value.substring(7).toLowerCase(Locale.ROOT) + "." + value.substring(0, 6);
  }

  private static String external(String value) {
    if (value == null || !value.matches("(?:sh|sz)\\.\\d{6}")) {
      throw new IllegalArgumentException("证券代码格式无效");
    }
    return value.substring(3) + "." + value.substring(0, 2).toUpperCase(Locale.ROOT);
  }

  /**
   * 公开协议解析测试及适配所需的原始供应商列，仅在供应商边界使用Map。
   *
   * @author chenxuefeng
   */
  public record SnapshotRows(
      /** 指定业务日的原始历史证券名册。 */
      List<Map<String, String>> basic,
      /** 指定日期的未复权日线。 */
      List<Map<String, String>> daily,
      /** 供应商明确的同日停复牌状态。 */
      List<Map<String, String>> suspended) {}
}
