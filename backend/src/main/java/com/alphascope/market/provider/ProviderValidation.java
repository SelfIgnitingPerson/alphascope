package com.alphascope.market.provider;

import com.alphascope.market.dto.DailyBarDto;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 所有通道共用的契约校验：空响应、错代码、零价、缺复权均视为通道失败。
 *
 * @author chenxuefeng
 */
public final class ProviderValidation {
  private ProviderValidation() {}

  public static List<DailyBarDto> bars(
      List<DailyBarDto> bars, String symbol, LocalDate from, LocalDate to) {
    if (bars == null || bars.isEmpty()) {
      throw new IllegalStateException("EMPTY_RESPONSE");
    }
    Set<LocalDate> dates = new HashSet<>();
    String source = null;
    for (var bar : bars) {
      if (bar == null
          || !symbol.equals(bar.symbol())
          || bar.tradeDate() == null
          || bar.tradeDate().isBefore(from)
          || bar.tradeDate().isAfter(to)
          || !dates.add(bar.tradeDate())
          || bar.provider() == null
          || bar.provider().isBlank()
          || bar.fetchedAt() == null
          || bar.isSynthetic()) {
        throw new IllegalStateException("INVALID_CONTRACT");
      }
      if (source == null) {
        source = bar.provider();
      } else if (!source.equals(bar.provider())) {
        throw new IllegalStateException("MIXED_BATCH_SOURCE");
      }
      for (var value :
          new java.math.BigDecimal[] {
            bar.open(), bar.high(), bar.low(), bar.close(), bar.adjustmentFactor()
          }) {
        if (value == null || value.signum() <= 0) {
          throw new IllegalStateException("INVALID_PRICE_OR_FACTOR");
        }
      }
      if (bar.volume() == null
          || bar.amount() == null
          || bar.volume().signum() < 0
          || bar.amount().signum() < 0
          || bar.high().compareTo(bar.low()) < 0
          || bar.high().compareTo(bar.open()) < 0
          || bar.high().compareTo(bar.close()) < 0
          || bar.low().compareTo(bar.open()) > 0
          || bar.low().compareTo(bar.close()) > 0) {
        throw new IllegalStateException("INVALID_OHLCV");
      }
    }
    return bars.stream().sorted(Comparator.comparing(DailyBarDto::tradeDate)).toList();
  }

  public static Map<LocalDate, Boolean> calendar(
      Map<LocalDate, Boolean> data, LocalDate from, LocalDate to) {
    if (data == null) {
      throw new IllegalStateException("EMPTY_CALENDAR");
    }
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      if (data.get(day) == null) {
        throw new IllegalStateException("INCOMPLETE_CALENDAR");
      }
    }
    if (data.keySet().stream().anyMatch(day -> day.isBefore(from) || day.isAfter(to))) {
      throw new IllegalStateException("INVALID_CALENDAR_RANGE");
    }
    return data;
  }
}
