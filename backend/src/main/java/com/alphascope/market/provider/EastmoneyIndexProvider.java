package com.alphascope.market.provider;

import com.alphascope.common.util.HttpBodies;
import com.alphascope.market.dto.DailyBarDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 东方财富指数日线备用能力，保留实际量额；不冒充股票因子或权威交易日历。
 *
 * @author chenxuefeng
 */
public final class EastmoneyIndexProvider implements MarketDataProvider {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
  private final ObjectMapper json = new ObjectMapper();

  @Override
  public String code() {
    return "EASTMONEY";
  }

  @Override
  public Set<MarketCapability> capabilities() {
    return Set.of(MarketCapability.INDEX_DAILY);
  }

  @Override
  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    throw new UnsupportedOperationException("EASTMONEY_STOCK_FACTORS_UNSUPPORTED");
  }

  @Override
  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    throw new UnsupportedOperationException("EASTMONEY_CALENDAR_UNSUPPORTED");
  }

  @Override
  public List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    if (!symbol.equals("000300.SH"))
      throw new UnsupportedOperationException("INDEX_NOT_REGISTERED");
    var output = new ArrayList<DailyBarDto>();
    // 按年请求，不能因供应商单页上限静默截断十年窗口。
    for (LocalDate start = from; !start.isAfter(to); ) {
      LocalDate end = start.withDayOfYear(start.lengthOfYear());
      if (end.isAfter(to)) end = to;
      URI uri =
          URI.create(
              "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=1.000300&klt=101&fqt=0&beg="
                  + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                  + "&end="
                  + end.format(DateTimeFormatter.BASIC_ISO_DATE)
                  + "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57&lmt=1000");
      try {
        var response =
            client.send(
                HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Referer", "https://quote.eastmoney.com/")
                    .GET()
                    .build(),
                HttpBodies.limited(4000000, Duration.ofSeconds(12)));
        {
          byte[] bytes = response.body();
          if (response.statusCode() != 200)
            throw new IllegalStateException("EASTMONEY_INDEX_HTTP_" + response.statusCode());
          if (bytes.length > 4000000)
            throw new IllegalStateException("EASTMONEY_INDEX_RESPONSE_INVALID");
          output.addAll(
              parse(
                  json.readTree(bytes),
                  symbol,
                  start,
                  end,
                  LocalDateTime.now(ZoneId.of("Asia/Shanghai"))));
        }
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("EASTMONEY_INTERRUPTED", error);
      } catch (IOException error) {
        throw new IllegalStateException("EASTMONEY_INDEX_UNAVAILABLE", error);
      }
      start = end.plusDays(1);
    }
    return ProviderValidation.bars(output, symbol, from, to);
  }

  /** 按接口字段顺序读取原始指数OHLC，成交量由手换为股，成交额为人民币元。 */
  public List<DailyBarDto> parse(
      JsonNode root, String symbol, LocalDate from, LocalDate to, LocalDateTime fetched) {
    JsonNode data = root.path("data");
    if (!data.path("code").asText().equals("000300") || !data.path("klines").isArray())
      throw new IllegalStateException("EASTMONEY_INDEX_MISMATCH");
    var result = new ArrayList<DailyBarDto>();
    for (var item : data.path("klines")) {
      String[] fields = item.asText().split(",", -1);
      if (fields.length < 7) throw new IllegalStateException("EASTMONEY_INDEX_FIELDS_MISSING");
      LocalDate date = LocalDate.parse(fields[0]);
      result.add(
          new DailyBarDto(
              symbol,
              date,
              new BigDecimal(fields[1]),
              new BigDecimal(fields[3]),
              new BigDecimal(fields[4]),
              new BigDecimal(fields[2]),
              new BigDecimal(fields[5]).multiply(BigDecimal.valueOf(100)),
              new BigDecimal(fields[6]),
              BigDecimal.ONE,
              code(),
              fetched,
              false));
    }
    return ProviderValidation.bars(result, symbol, from, to);
  }

  @Override
  public void close() {
    client.close();
  }
}
