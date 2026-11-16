package com.alphascope.market.provider;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.provider.baostock.BaoStockClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 免费BaoStock通道；原价与后复权因子由同一供应商取得，成交量股、成交额元。
 *
 * @author chenxuefeng
 */
public final class BaoStockMarketDataProvider implements MarketDataProvider {
  private final BaoStockClient client;

  public BaoStockMarketDataProvider(BaoStockClient client) {
    this.client = client;
  }

  public String code() {
    return "BAOSTOCK";
  }

  public void close() {
    client.close();
  }

  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    Map<LocalDate, Boolean> result = new TreeMap<>();
    for (var row : client.query("33", "query_trade_dates", 9, from.toString(), to.toString())) {
      result.put(LocalDate.parse(row.get("calendar_date")), "1".equals(row.get("is_trading_day")));
    }
    return ProviderValidation.calendar(result, from, to);
  }

  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    return fetchWindow(symbol, from, to, false).bars();
  }

  public List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    return fetchWindow(symbol, from, to, true).bars();
  }

  public MarketWindow fetchWindow(String symbol, LocalDate from, LocalDate to, boolean index) {
    if (!symbol.matches("[0-9]{6}\\.(SH|SZ)")) {
      throw new IllegalArgumentException("不支持的BaoStock代码");
    }
    String[] split = symbol.split("\\.");
    String code = split[1].toLowerCase(Locale.ROOT) + "." + split[0];
    String fields = "date,open,high,low,close,volume,amount" + (index ? "" : ",tradestatus");
    var rows =
        client.query(
            "95",
            "query_history_k_data_plus",
            8,
            code,
            fields,
            from.toString(),
            to.toString(),
            "d",
            "3");
    NavigableMap<LocalDate, BigDecimal> factors = new TreeMap<>();
    factors.put(LocalDate.MIN, BigDecimal.ONE);
    if (!index) {
      for (var row :
          client.query("15", "query_adjust_factor", 10, code, "1990-01-01", to.toString())) {
        factors.put(
            LocalDate.parse(row.get("dividOperateDate")),
            new BigDecimal(row.get("backAdjustFactor")));
      }
    }
    List<DailyBarDto> result = new ArrayList<>();
    Map<LocalDate, String> absences = new TreeMap<>();
    for (var row : rows) {
      if (!index && "0".equals(row.get("tradestatus"))) {
        absences.put(LocalDate.parse(row.get("date")), "SUSPENDED");
        continue;
      }
      LocalDate date = LocalDate.parse(row.get("date"));
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
              factors.floorEntry(date).getValue(),
              code(),
              LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
              false));
    }
    return new MarketWindow(
        result.isEmpty() ? List.of() : ProviderValidation.bars(result, symbol, from, to), absences);
  }

  private BigDecimal n(Map<String, String> row, String key) {
    try {
      return new BigDecimal(row.get(key));
    } catch (Exception e) {
      throw new IllegalStateException("BAOSTOCK_INVALID_NUMERIC", e);
    }
  }
}
