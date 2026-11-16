package com.alphascope.market.provider;

import com.alphascope.market.dto.DailyBarDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性演示数据，与真实交易日历隔离；绝不产生真实风险结论。
 *
 * @author chenxuefeng
 */
public class DemoMarketDataProvider implements MarketDataProvider {
  @Override
  public boolean synthetic() {
    return true;
  }

  public String code() {
    return "DEMO";
  }

  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    Map<LocalDate, Boolean> m = new LinkedHashMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      m.put(d, d.getDayOfWeek().getValue() < 6);
    }
    return m;
  }

  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    List<DailyBarDto> bars = new ArrayList<>();
    fetchCalendar(from, to)
        .forEach(
            (d, open) -> {
              if (open) {
                double t = d.toEpochDay();
                double p =
                    40
                        + Math.floorMod(symbol.hashCode(), 40)
                        + Math.sin(t / 12) * 5
                        + t % 1000 / 100;
                BigDecimal c = BigDecimal.valueOf(p).setScale(4, RoundingMode.HALF_UP);
                BigDecimal v =
                    BigDecimal.valueOf(1000000 + Math.floorMod(d.toEpochDay() * 971, 1000000));
                bars.add(
                    new DailyBarDto(
                        symbol,
                        d,
                        c,
                        c.add(BigDecimal.ONE),
                        c.subtract(BigDecimal.ONE),
                        c,
                        v,
                        c.multiply(v),
                        BigDecimal.ONE,
                        code(),
                        LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
                        true));
              }
            });
    return bars;
  }
}
