package com.alphascope.market.provider;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.TushareRequest;
import com.alphascope.market.provider.tushare.TushareClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 官方HTTP接口适配：按月分页，串行限速，最多三次重试；单位只在边界转换一次。
 *
 * @author chenxuefeng
 */
public class TushareMarketDataProvider implements MarketDataProvider {
  private final TushareClient client;

  public TushareMarketDataProvider(String token) {
    this(new TushareClient(token));
  }

  public TushareMarketDataProvider(TushareClient client) {
    this.client = client;
  }

  @Override
  public String code() {
    return "TUSHARE";
  }

  private String date(LocalDate value) {
    return value.format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  private List<Map<String, String>> query(String api, Map<String, String> params) {
    return client.query(TushareRequest.builder().api(api).parameters(params).build());
  }

  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    Map<LocalDate, Boolean> out = new TreeMap<>();
    for (var row :
        query(
            "trade_cal",
            Map.of("exchange", "SSE", "start_date", date(from), "end_date", date(to)))) {
      out.put(
          LocalDate.parse(row.get("cal_date"), DateTimeFormatter.BASIC_ISO_DATE),
          "1".equals(row.get("is_open")));
    }
    return out;
  }

  private BigDecimal number(Map<String, String> row, String key) {
    String v = row.get(key);
    if (v == null) {
      throw new IllegalStateException("行情缺字段: " + key);
    }
    return new BigDecimal(v);
  }

  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    return fetch(symbol, from, to, false);
  }

  public List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    return fetch(symbol, from, to, true);
  }

  private List<DailyBarDto> fetch(String symbol, LocalDate from, LocalDate to, boolean index) {
    List<DailyBarDto> out = new ArrayList<>();
    for (LocalDate start = from; !start.isAfter(to); ) {
      LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
      if (end.isAfter(to)) {
        end = to;
      }
      var params = Map.of("ts_code", symbol, "start_date", date(start), "end_date", date(end));
      Map<String, BigDecimal> factors = new HashMap<>();
      if (!index) {
        for (var row : query("adj_factor", params)) {
          factors.put(row.get("trade_date"), number(row, "adj_factor"));
        }
      }
      for (var row : query(index ? "index_daily" : "daily", params)) {
        BigDecimal factor = index ? BigDecimal.ONE : factors.get(row.get("trade_date"));
        if (factor == null) {
          throw new IllegalStateException("复权因子缺失");
        }
        out.add(
            new DailyBarDto(
                symbol,
                LocalDate.parse(row.get("trade_date"), DateTimeFormatter.BASIC_ISO_DATE),
                number(row, "open"),
                number(row, "high"),
                number(row, "low"),
                number(row, "close"),
                number(row, "vol").multiply(BigDecimal.valueOf(100)),
                number(row, "amount").multiply(BigDecimal.valueOf(1000)),
                factor,
                code(),
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
                false));
      }
      start = end.plusDays(1);
    }
    out.sort(Comparator.comparing(DailyBarDto::tradeDate));
    return out;
  }
}
