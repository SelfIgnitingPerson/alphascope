package com.alphascope.market.provider;

import com.alphascope.common.util.HttpBodies;
import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.provider.sina.SinaDecoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * 直接HTTP接入新浪，固定算法在JVM内解码，原始OHLCV/amount和真实因子均来自新浪。
 *
 * @author chenxuefeng
 */
public final class SinaMarketDataProvider implements MarketDataProvider {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final SinaDecoder decoder = new SinaDecoder();
  private final ObjectMapper json = new ObjectMapper();

  public String code() {
    return "SINA";
  }

  public boolean supportsIndexBars() {
    return false;
  }

  public void close() {
    client.close();
  }

  private String get(String url) {
    for (int retry = 0; retry < 2; retry++) {
      try {
        var response =
            client.send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 AlphaScope/1.0")
                    .header("Referer", "https://finance.sina.com.cn/")
                    .GET()
                    .build(),
                HttpBodies.limited(20_000_000, Duration.ofSeconds(20)));
        if (response.statusCode() != 200)
          throw new IllegalStateException("SINA_HTTP_" + response.statusCode());
        if (response.body().length > 20_000_000)
          throw new IllegalStateException("SINA_RESPONSE_INVALID");
        return new String(response.body(), StandardCharsets.UTF_8);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("SINA_INTERRUPTED");
      } catch (Exception e) {
        if (retry == 1) {
          throw new IllegalStateException("SINA_UNAVAILABLE", e);
        }
      }
    }
    throw new IllegalStateException("SINA_UNAVAILABLE");
  }

  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    var dates =
        decoder.decode(
            SinaDecoder.assignmentData(
                get("https://finance.sina.com.cn/realstock/company/klc_td_sh.txt")));
    Set<LocalDate> open = new HashSet<>();
    for (var value : dates) {
      open.add(LocalDate.parse(value.asText().substring(0, 10)));
    }
    open.add(LocalDate.of(1992, 5, 4));
    if (open.stream().min(LocalDate::compareTo).orElseThrow().isAfter(from)
        || open.stream().max(LocalDate::compareTo).orElseThrow().isBefore(to)) {
      throw new IllegalStateException("SINA_CALENDAR_INSUFFICIENT");
    }
    Map<LocalDate, Boolean> result = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      result.put(d, open.contains(d));
    }
    return result;
  }

  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    return fetch(symbol, from, to);
  }

  public List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    // 此公开压缩接口没有可靠的指数成交额，不能用0补齐或混入另一家字段。
    throw new UnsupportedOperationException("SINA_INDEX_AMOUNT_UNAVAILABLE");
  }

  private List<DailyBarDto> fetch(String symbol, LocalDate from, LocalDate to) {
    if (!symbol.matches("[0-9]{6}\\.(SH|SZ|BJ)")) {
      throw new IllegalArgumentException("无效股票代码");
    }
    String[] parts = symbol.split("\\.");
    String code = parts[1].toLowerCase(Locale.ROOT) + parts[0];
    JsonNode rows =
        decoder.decode(
            SinaDecoder.assignmentData(
                get(
                    "https://finance.sina.com.cn/realstock/company/"
                        + code
                        + "/hisdata_klc2/klc_kl.js")));
    NavigableMap<LocalDate, BigDecimal> factors = new TreeMap<>();
    {
      String text = get("https://finance.sina.com.cn/realstock/company/" + code + "/hfq.js");
      try {
        int a = text.indexOf('{'), z = text.lastIndexOf('}');
        if (a < 0 || z < a) {
          throw new IllegalStateException("SINA_FACTOR_INVALID");
        }
        var data = json.readTree(text.substring(a, z + 1)).path("data");
        if (!data.isArray() || data.isEmpty()) {
          throw new IllegalStateException("SINA_FACTOR_MISSING");
        }
        for (var row : data) {
          factors.put(
              LocalDate.parse(row.path("d").asText()), new BigDecimal(row.path("f").asText()));
        }
      } catch (IOException e) {
        throw new IllegalStateException("SINA_FACTOR_INVALID", e);
      }
    }
    List<DailyBarDto> result = new ArrayList<>();
    for (var row : rows) {
      LocalDate date = LocalDate.parse(row.path("date").asText().substring(0, 10));
      if (date.isBefore(from) || date.isAfter(to)) {
        continue;
      }
      var factor = factors.floorEntry(date);
      if (factor == null) {
        throw new IllegalStateException("SINA_FACTOR_MISSING");
      }
      result.add(
          new DailyBarDto(
              symbol,
              date,
              n(row, "open"),
              n(row, "high"),
              n(row, "low"),
              n(row, "close"),
              n(row, "volume"),
              n(row, "amount"),
              factor.getValue(),
              code(),
              LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
              false));
    }
    return ProviderValidation.bars(result, symbol, from, to);
  }

  private BigDecimal n(JsonNode row, String field) {
    try {
      if (row.get(field) == null || row.get(field).isNull()) {
        throw new IllegalStateException();
      }
      return new BigDecimal(row.get(field).asText());
    } catch (Exception e) {
      throw new IllegalStateException("SINA_MISSING_" + field.toUpperCase(Locale.ROOT), e);
    }
  }
}
