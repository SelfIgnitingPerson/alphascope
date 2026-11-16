package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 无外网固定证据验证：失败、空值、错复权、安全模式与熔断恢复。
 *
 * @author chenxuefeng
 */
class MultiChannelProviderTest {
  static final LocalDate DATE = LocalDate.of(2025, 1, 2);
  static final String SYMBOL = "300476.SZ";

  static DailyBarDto bar(String provider, BigDecimal factor, boolean synthetic) {
    return new DailyBarDto(
        SYMBOL,
        DATE,
        new BigDecimal("10"),
        new BigDecimal("11"),
        new BigDecimal("9"),
        new BigDecimal("10.5"),
        new BigDecimal("100"),
        new BigDecimal("1050"),
        factor,
        provider,
        DATE.atTime(16, 30),
        synthetic);
  }

  /**
   * 可控制成功、故障及调用次数的测试行情通道。
   *
   * @author chenxuefeng
   */
  static class Channel implements MarketDataProvider {
    final String name;
    final AtomicInteger calls = new AtomicInteger();
    boolean broken;
    List<DailyBarDto> rows;

    Channel(String name, boolean broken) {
      this.name = name;
      this.broken = broken;
      this.rows = List.of(bar(name, BigDecimal.ONE, false));
    }

    public String code() {
      return name;
    }

    public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
      calls.incrementAndGet();
      if (broken) throw new IllegalStateException("UPSTREAM_UNAVAILABLE");
      return rows;
    }

    public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
      return Map.of(DATE, true);
    }
  }

  /**
   * 可推进时间的测试时钟，用于验证通道冷却和重试行为。
   *
   * @author chenxuefeng
   */
  static class MutableClock extends Clock {
    Instant now = Instant.parse("2025-01-02T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  MultiChannelMarketDataProvider multi(Channel... channels) {
    return new MultiChannelMarketDataProvider(
        List.of(channels), Duration.ofSeconds(30), Clock.systemUTC());
  }

  @Test
  void healthyPrimaryDoesNotCallBackup() {
    var a = new Channel("PRIMARY", false);
    var b = new Channel("BACKUP", false);
    assertEquals("PRIMARY", multi(a, b).fetchDailyBars(SYMBOL, DATE, DATE).get(0).provider());
    assertEquals(0, b.calls.get());
  }

  @Test
  void failureFallsBackWithActualSource() {
    var a = new Channel("PRIMARY", true);
    var b = new Channel("BACKUP", false);
    var provider = multi(a, b);
    assertEquals("BACKUP", provider.fetchDailyBars(SYMBOL, DATE, DATE).get(0).provider());
    assertFalse(provider.synthetic());
    assertTrue(provider.scope().contains("BACKUP"));
  }

  @Test
  void emptyResponseFallsBack() {
    var a = new Channel("PRIMARY", false);
    a.rows = List.of();
    assertEquals(
        "BACKUP",
        multi(a, new Channel("BACKUP", false))
            .fetchDailyBars(SYMBOL, DATE, DATE)
            .get(0)
            .provider());
  }

  @Test
  void missingFactorFallsBack() {
    var a = new Channel("PRIMARY", false);
    a.rows = List.of(bar("PRIMARY", null, false));
    assertEquals(
        "BACKUP",
        multi(a, new Channel("BACKUP", false))
            .fetchDailyBars(SYMBOL, DATE, DATE)
            .get(0)
            .provider());
  }

  @Test
  void syntheticRowsFromRealChannelAreRejected() {
    var a = new Channel("PRIMARY", false);
    a.rows = List.of(bar("PRIMARY", BigDecimal.ONE, true));
    assertThrows(IllegalStateException.class, () -> multi(a).fetchDailyBars(SYMBOL, DATE, DATE));
  }

  @Test
  void allFailuresNeverBecomeDemo() {
    assertThrows(
        IllegalStateException.class,
        () ->
            multi(new Channel("ONE", true), new Channel("TWO", true))
                .fetchDailyBars(SYMBOL, DATE, DATE));
  }

  @Test
  void demoCannotBeConfiguredAsRealFallback() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MultiChannelMarketDataProvider(
                List.of(new DemoMarketDataProvider()), Duration.ZERO, Clock.systemUTC()));
  }

  @Test
  void circuitSkipsThenRecovers() {
    var clock = new MutableClock();
    var a = new Channel("PRIMARY", true);
    var b = new Channel("BACKUP", false);
    var p = new MultiChannelMarketDataProvider(List.of(a, b), Duration.ofSeconds(30), clock);
    p.fetchDailyBars(SYMBOL, DATE, DATE);
    a.broken = false;
    p.fetchDailyBars(SYMBOL, DATE, DATE);
    assertEquals(1, a.calls.get());
    clock.now = clock.now.plusSeconds(31);
    assertEquals("PRIMARY", p.fetchDailyBars(SYMBOL, DATE, DATE).get(0).provider());
    assertEquals(2, a.calls.get());
  }

  @Test
  void partialCalendarIsNotFilledWithWeekdays() {
    var p = multi(new Channel("ONE", false));
    assertThrows(IllegalStateException.class, () -> p.fetchCalendar(DATE, DATE.plusDays(1)));
  }

  @Test
  void incompleteCalendarDoesNotCoolDownHealthyStockCapability() {
    var channel = new Channel("ONE", false);
    var provider = multi(channel);
    assertThrows(IllegalStateException.class, () -> provider.fetchCalendar(DATE, DATE.plusDays(1)));
    assertEquals("ONE", provider.fetchDailyBars(SYMBOL, DATE, DATE).getFirst().provider());
    assertEquals(1, channel.calls.get());
  }

  @Test
  void twoSourcesCannotBeSplicedInOneBatch() {
    var first = bar("ONE", BigDecimal.ONE, false);
    var second =
        new DailyBarDto(
            SYMBOL,
            DATE.plusDays(1),
            first.open(),
            first.high(),
            first.low(),
            first.close(),
            first.volume(),
            first.amount(),
            first.adjustmentFactor(),
            "TWO",
            first.fetchedAt(),
            false);
    assertThrows(
        IllegalStateException.class,
        () -> ProviderValidation.bars(List.of(first, second), SYMBOL, DATE, DATE.plusDays(1)));
  }
}
