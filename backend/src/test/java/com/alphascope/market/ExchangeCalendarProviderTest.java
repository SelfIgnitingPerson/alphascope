package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.alphascope.market.provider.ExchangeCalendarProvider;
import com.alphascope.market.provider.LicensedMarketProvider;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.operations.service.SourceLicenseService;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 官方年历与周末调休边界核验，不将未复核年份推算成真实日历。
 *
 * @author chenxuefeng
 */
class ExchangeCalendarProviderTest {
  @Test
  void officialHolidayAndWeekendWorkdayAreClosed() {
    var provider = new ExchangeCalendarProvider();
    var days = provider.fetchCalendar(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 24));
    assertTrue(days.get(LocalDate.of(2026, 2, 13)));
    assertFalse(days.get(LocalDate.of(2026, 2, 14)));
    assertFalse(days.get(LocalDate.of(2026, 2, 23)));
    assertTrue(days.get(LocalDate.of(2026, 2, 24)));
    assertEquals(12, days.size());
  }

  @Test
  void differentAnnualSchedulesAreNotReplacedByOneGenericHolidayRule() {
    var provider = new ExchangeCalendarProvider();
    assertFalse(
        provider
            .fetchCalendar(LocalDate.of(2024, 2, 9), LocalDate.of(2024, 2, 9))
            .values()
            .iterator()
            .next());
    assertTrue(
        provider
            .fetchCalendar(LocalDate.of(2025, 2, 5), LocalDate.of(2025, 2, 5))
            .values()
            .iterator()
            .next());
    assertFalse(
        provider
            .fetchCalendar(LocalDate.of(2025, 10, 8), LocalDate.of(2025, 10, 8))
            .values()
            .iterator()
            .next());
    assertTrue(
        provider
            .fetchCalendar(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8))
            .values()
            .iterator()
            .next());
  }

  @Test
  void unreviewedYearAndPartialCrossYearWindowAreRejected() {
    var provider = new ExchangeCalendarProvider();
    assertThrows(
        IllegalStateException.class,
        () -> provider.fetchCalendar(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 4)));
    assertThrows(
        IllegalStateException.class,
        () -> provider.fetchCalendar(LocalDate.of(2023, 12, 29), LocalDate.of(2024, 1, 2)));
    assertEquals(Set.of(MarketCapability.TRADING_CALENDAR), provider.capabilities());
  }

  @Test
  void permissionWrapperPreservesReviewAndContentFingerprint() {
    var provider = new ExchangeCalendarProvider();
    var wrapped = new LicensedMarketProvider(provider, mock(SourceLicenseService.class));
    assertEquals(provider.calendarSource(), wrapped.calendarSource());
    assertTrue(wrapped.calendarSource().matches("EXCHANGE_CALENDAR_20261110_[a-f0-9]{12}"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> provider.fetchIndexBars("000300.SH", LocalDate.now(), LocalDate.now()));
  }
}
