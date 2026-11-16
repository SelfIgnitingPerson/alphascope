package com.alphascope.market.policy;

import com.alphascope.market.dto.MarketBarDO;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 缓存必须完整覆盖期望交易日，时间、来源、复权因子和真实数据标识均须有效。
 *
 * @author chenxuefeng
 */
public final class MarketCachePolicy {
  private MarketCachePolicy() {}

  /** 历史回放只放宽缓存年龄，保留完整日期、同源、OHLC和真实数据校验。 */
  public static boolean storedHistoricalUsable(
      List<MarketBarDO> rows, Set<LocalDate> expected, LocalDateTime now, LocalDate end) {
    if (!end.isBefore(now.toLocalDate()) || !complete(rows, expected, now, null)) {
      return false;
    }
    for (MarketBarDO row : rows) {
      if (!Boolean.FALSE.equals(row.getSynthetic())
          || "DEMO".equals(row.getSource())
          || !positive(row.getOpen())
          || !positive(row.getHigh())
          || !positive(row.getLow())
          || !positive(row.getClose())
          || row.getHigh().compareTo(row.getOpen().max(row.getClose())) < 0
          || row.getLow().compareTo(row.getOpen().min(row.getClose())) > 0
          || row.getHigh().compareTo(row.getLow()) < 0
          || !nonNegative(row.getVolume())
          || !nonNegative(row.getAmount())
          || row.getTradeDate().isAfter(end)) {
        return false;
      }
    }
    return true;
  }

  private static boolean positive(Number value) {
    return value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0;
  }

  private static boolean nonNegative(Number value) {
    return value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0;
  }

  public static boolean usable(
      List<MarketBarDO> rows, Set<LocalDate> expected, LocalDateTime now, Duration ttl) {
    return !rows.isEmpty()
        && !expected.isEmpty()
        && !ttl.isZero()
        && !ttl.isNegative()
        && complete(rows, expected, now, ttl);
  }

  private static boolean complete(
      List<MarketBarDO> rows, Set<LocalDate> expected, LocalDateTime now, Duration ttl) {
    if (rows.isEmpty() || expected.isEmpty()) {
      return false;
    }
    Set<LocalDate> actual = new HashSet<>();
    String source = rows.getFirst().getSource();
    for (MarketBarDO row : rows) {
      LocalDateTime fetchedAt = row.getFetchedAt();
      if (source == null
          || !Objects.equals(source, row.getSource())
          || !positive(row.getFactor())
          || row.getTradeDate() == null
          || !actual.add(row.getTradeDate())
          || fetchedAt == null
          || fetchedAt.isAfter(now)
          || ttl != null && fetchedAt.plus(ttl).isBefore(now)) {
        return false;
      }
    }
    return actual.equals(expected);
  }
}
