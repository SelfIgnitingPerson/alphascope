package com.alphascope.fundamental.provider;

import com.alphascope.common.util.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 东方财富官方盈利预测页同源公开接口。只接收E类预测，不把A类实际EPS当成预测。
 *
 * @author chenxuefeng
 */
public final class EastmoneyForecastProvider implements AutoCloseable {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final URI endpoint;
  private final ObjectMapper json = new ObjectMapper();

  public EastmoneyForecastProvider() {
    this(URI.create("https://datacenter-web.eastmoney.com/api/data/v1/get"));
  }

  public EastmoneyForecastProvider(URI endpoint) {
    this.endpoint = endpoint;
  }

  /**
   * 单个财务年度的公开汇总预测EPS；不是已核验逐家机构的一致预期。
   *
   * @author chenxuefeng
   */
  public record Estimate(
      /** 预测对应的财务年度，不是快照采集年份。 */
      int year,

      /** 该年度公开汇总预测每股收益，单位元/股；未核验逐家机构的一致口径。 */
      double eps) {}

  /**
   * 供应商当前公开盈利预测响应及其原始证据。
   *
   * @author chenxuefeng
   */
  public record Forecast(
      /** 近六个月评级机构数，不等于每个年度EPS的实际参与机构数。 */
      int ratingAgencies,

      /** 按财务年度排列的公开汇总预测EPS列表。 */
      List<Estimate> estimates,

      /** 本次供应商原始响应文本，供不可变快照归档。 */
      String raw,

      /** 本次原始响应内容的SHA-256指纹。 */
      String hash,

      /** 可核验的公开原始来源URL。 */
      String sourceUrl) {}

  public Forecast fetch(String symbol) {
    if (!symbol.matches("[0-9]{6}\\.(SH|SZ|BJ)")) {
      throw new IllegalArgumentException("无效证券代码");
    }
    try {
      String query =
          "reportName=RPT_WEB_RESPREDICT&columns=ALL&pageNumber=1&pageSize=10&filter="
              + URLEncoder.encode(
                  "(SECURITY_CODE=\"" + symbol.substring(0, 6) + "\")", StandardCharsets.UTF_8);
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(endpoint + "?" + query))
                  .timeout(Duration.ofSeconds(15))
                  .header("User-Agent", "AlphaScope/0.1 research")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200 || response.body().length() > 2_000_000) {
        throw new IllegalStateException("预测接口响应异常");
      }
      var root = json.readTree(response.body());
      if (!root.path("success").asBoolean()) {
        throw new IllegalStateException("预测接口未返回成功状态");
      }
      return parse(symbol, root.path("result").path("data"));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("预测采集中断", e);
    } catch (IOException e) {
      throw new IllegalStateException("预测接口不可用", e);
    }
  }

  public static Forecast parse(String symbol, JsonNode rows) {
    if (!rows.isArray()) {
      throw new IllegalArgumentException("预测数据集缺失");
    }
    JsonNode selected = null;
    for (var row : rows) {
      if (symbol.equals(row.path("SECUCODE").asText())) {
        if (selected != null) {
          throw new IllegalArgumentException("预测证券重复");
        }
        selected = row;
      }
    }
    if (selected == null) {
      throw new IllegalArgumentException("该证券无公开预测记录");
    }
    List<Estimate> estimates = new ArrayList<>();
    Set<Integer> years = new HashSet<>();
    for (int i = 1; i <= 4; i++) {
      if (selected.path("YEAR_MARK" + i).asText().equals("E")) {
        int year = selected.path("YEAR" + i).asInt();
        JsonNode eps = selected.path("EPS" + i);
        if (year < 2000
            || year > 2100
            || !eps.isNumber()
            || !Double.isFinite(eps.doubleValue())
            || !years.add(year)) {
          throw new IllegalArgumentException("预测年份或EPS无效");
        }
        estimates.add(new Estimate(year, eps.doubleValue()));
      }
    }
    JsonNode org = selected.path("RATING_ORG_NUM");
    if (estimates.isEmpty() || !org.isIntegralNumber() || org.asInt() < 0) {
      throw new IllegalArgumentException("预测记录不完整");
    }
    estimates.sort(Comparator.comparingInt(Estimate::year));
    String raw = Jsons.write(selected);
    return new Forecast(
        org.asInt(),
        estimates,
        raw,
        Jsons.hash(raw),
        "https://data.eastmoney.com/report/profitforecast.jshtml");
  }

  public void close() {
    http.close();
  }
}
