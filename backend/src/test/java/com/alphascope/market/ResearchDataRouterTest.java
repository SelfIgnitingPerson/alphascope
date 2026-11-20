package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.task.TaskStopReason;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.research.ResearchDataProvider;
import com.alphascope.market.provider.research.ResearchDataRouter;
import java.time.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 全市场能力按来源和能力独立降级，禁止因子跨源拼接、取消后重试及访问受限后重连。
 *
 * @author chenxuefeng
 */
class ResearchDataRouterTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void fullSnapshotRetriesTransientDisconnectThreeTimesBeforeSwitchingSource() {
    var primary = provider("EASTMONEY");
    var partial = provider("BAOSTOCK");
    var alternate = provider("TUSHARE");
    when(primary.fullMarketSnapshots()).thenReturn(true);
    when(alternate.fullMarketSnapshots()).thenReturn(true);
    when(primary.snapshot(date)).thenThrow(new IllegalStateException(new java.io.EOFException()));
    when(alternate.snapshot(date)).thenReturn(snapshot("TUSHARE"));
    String result =
        router(primary, partial, alternate).fullSnapshot(date, ResearchMarketSnapshot::source);
    assertEquals("TUSHARE", result);
    var order = inOrder(primary, alternate);
    order.verify(primary, times(3)).snapshot(date);
    order.verify(alternate).snapshot(date);
    verify(partial, never()).snapshot(any());
  }

  @Test
  void fullSnapshotSwitchesAfterCoverageFailureWithoutRepeatingInvalidResponse() {
    var primary = provider("EASTMONEY");
    var alternate = provider("TUSHARE");
    when(primary.fullMarketSnapshots()).thenReturn(true);
    when(alternate.fullMarketSnapshots()).thenReturn(true);
    when(primary.snapshot(date)).thenReturn(snapshot("EASTMONEY"));
    when(alternate.snapshot(date)).thenReturn(snapshot("TUSHARE"));
    String result =
        router(primary, alternate)
            .fullSnapshot(
                date,
                candidate -> {
                  if ("EASTMONEY".equals(candidate.source()))
                    throw new IllegalStateException("FULL_A_DAILY_COVERAGE_INCOMPLETE");
                  return candidate.source();
                });
    assertEquals("TUSHARE", result);
    verify(primary).snapshot(date);
  }

  @Test
  void snapshotFailureDoesNotCoolDownTheSameProvidersFactorCapability() {
    var first = provider("FIRST");
    var second = provider("SECOND");
    var router = router(first, second);
    when(first.snapshot(date)).thenThrow(new IllegalStateException("network"));
    when(second.snapshot(date)).thenReturn(snapshot("SECOND"));
    when(first.factors(any()))
        .thenReturn(new ResearchFactorWindow("FIRST", date, date, "[]", "[]"));
    assertEquals("SECOND", router.snapshot(date, null).source());
    assertEquals("FIRST", router.factors(request("FIRST")).source());
    assertEquals("COOLDOWN", router.status("FIRST").get(0).state());
    assertEquals("AVAILABLE", router.status("FIRST").get(1).state());
  }

  @Test
  void factorRequestCannotSwitchToADifferentSource() {
    var first = provider("FIRST");
    var second = provider("SECOND");
    var router = router(first, second);
    when(first.factors(any())).thenThrow(new IllegalStateException("unavailable"));
    assertThrows(IllegalStateException.class, () -> router.factors(request("FIRST")));
    verify(second, never()).factors(any());
  }

  @Test
  void returnedFactorSourceMustMatchTheActualProvider() {
    var first = provider("FIRST");
    var router = router(first);
    when(first.factors(any()))
        .thenReturn(new ResearchFactorWindow("OTHER", date, date, "[]", "[]"));
    assertThrows(IllegalStateException.class, () -> router.factors(request("FIRST")));
  }

  @Test
  void aMissingSecurityDoesNotDisableFactorsForEveryOtherSecurity() {
    var first = provider("FIRST");
    var router = router(first);
    when(first.factors(any()))
        .thenThrow(new ProviderDataUnavailableException("NO_DATA"))
        .thenReturn(new ResearchFactorWindow("FIRST", date, date, "[]", "[]"));
    assertThrows(IllegalStateException.class, () -> router.factors(request("FIRST")));
    assertEquals("FIRST", router.factors(request("FIRST")).source());
    verify(first, times(2)).factors(any());
  }

  @Test
  void persistentBaoStockRestrictionIsNeverBypassed() {
    var first = provider("BAOSTOCK");
    var second = provider("SECOND");
    var router = router(first, second);
    when(second.snapshot(date)).thenReturn(snapshot("SECOND"));
    var guard = mock(BaoStockAccessGuard.class);
    when(guard.blocked()).thenReturn(true);
    when(guard.state())
        .thenReturn(new BaoStockAccessGuard.AccessState(true, "10001011", null, null));
    try (var standard = mockStatic(BaoStockAccessGuard.class)) {
      standard.when(BaoStockAccessGuard::standard).thenReturn(guard);
      assertEquals("SECOND", router.snapshot(date, null).source());
      verify(first, never()).snapshot(any());
      assertEquals("ACCESS_RESTRICTED", router.status("BAOSTOCK").getFirst().state());
      var savedError = router.status("BAOSTOCK").getFirst().diagnostic().providerError();
      assertEquals("10001011", savedError.code());
      assertEquals("NOT_RECORDED", savedError.messageState());
    }
  }

  @Test
  void cancellationDoesNotTriggerAFallbackRequest() {
    var first = provider("FIRST");
    var second = provider("SECOND");
    var router = router(first, second);
    when(first.snapshot(date)).thenThrow(new TaskStoppedException(TaskStopReason.CANCELLED));
    assertThrows(TaskStoppedException.class, () -> router.snapshot(date, null));
    verify(second, never()).snapshot(any());
  }

  @Test
  void undeclaredCapabilitiesAreExplicitAndStatusQueriesDoNotFetch() {
    var first = provider("FIRST");
    when(first.capabilities()).thenReturn(Set.of(MarketCapability.MARKET_SNAPSHOT));
    var router = router(first);
    assertFalse(router.status("FIRST").get(1).supported());
    assertThrows(IllegalStateException.class, () -> router.factors(request("FIRST")));
    verify(first, never()).snapshot(any());
    verify(first, never()).factors(any());
  }

  @Test
  void explicitAccessCooldownSurvivesOrdinaryCapabilityRetryWindows() {
    var first = provider("EASTMONEY");
    var second = provider("SECOND");
    when(first.blockedUntil()).thenReturn(clock.instant().plusSeconds(3600));
    when(second.snapshot(date)).thenReturn(snapshot("SECOND"));
    var router = router(first, second);
    assertEquals("SECOND", router.snapshot(date, null).source());
    verify(first, never()).snapshot(any());
    assertEquals("ACCESS_COOLDOWN", router.status("EASTMONEY").getFirst().state());
    assertEquals(
        clock.instant().plusSeconds(3600), router.status("EASTMONEY").getFirst().retryAt());
  }

  @Test
  void independentRosterSourceRequiresItsOwnCollectionPermission() {
    var first = provider("EASTMONEY");
    when(first.requiredSources()).thenReturn(Set.of("EASTMONEY", "SINA"));
    var licenses = mock(com.alphascope.operations.service.SourceLicenseService.class);
    doThrow(new IllegalStateException("MISSING_ROSTER_PERMISSION"))
        .when(licenses)
        .require("SINA", "COLLECT");
    var router = router(first);
    router.setLicenses(licenses);
    assertThrows(IllegalStateException.class, () -> router.snapshot(date, null));
    verify(first, never()).snapshot(any());
    verify(licenses).require("SINA", "COLLECT");
  }

  private ResearchDataProvider provider(String code) {
    var value = mock(ResearchDataProvider.class);
    when(value.code()).thenReturn(code);
    when(value.capabilities())
        .thenReturn(Set.of(MarketCapability.MARKET_SNAPSHOT, MarketCapability.ADJUSTMENT_FACTORS));
    return value;
  }

  private ResearchDataRouter router(ResearchDataProvider... providers) {
    return new ResearchDataRouter(List.of(providers), Duration.ofSeconds(30), clock);
  }

  private ResearchMarketSnapshot snapshot(String source) {
    return ResearchMarketSnapshot.builder()
        .source(source)
        .date(date)
        .universe(List.of())
        .daily(List.of())
        .build();
  }

  private ResearchFactorRequest request(String source) {
    return ResearchFactorRequest.builder()
        .symbol("sh.600000")
        .start(date)
        .end(date)
        .source(source)
        .build();
  }
}
