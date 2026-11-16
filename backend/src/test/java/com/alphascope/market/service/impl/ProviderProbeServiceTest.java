package com.alphascope.market.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.vo.CapabilityView;
import com.alphascope.market.vo.ProviderChannelView;
import com.alphascope.market.vo.ProviderProbeSample;
import java.time.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 无外网核验权限、无队列限频、超时退出、错误安全与指定能力保护。
 *
 * @author chenxuefeng
 */
class ProviderProbeServiceTest {
  private static final LocalDate DATE = LocalDate.of(2026, 11, 12);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T01:00:00Z"), ZoneOffset.UTC);
  private static final UserView ADMIN = new UserView(7, "probe-admin", "ADMIN");
  private final MarketDataProvider provider = mock(MarketDataProvider.class);
  private final ProviderProbeServiceImpl service =
      new ProviderProbeServiceImpl(provider, CLOCK, Duration.ofMillis(300));

  @AfterEach
  void close() {
    service.close();
  }

  private ProviderProbeRequest request() {
    return new ProviderProbeRequest("SINA", MarketCapability.STOCK_DAILY, DATE);
  }

  private void status(String state) {
    when(provider.status())
        .thenReturn(
            List.of(
                new ProviderChannelView(
                    "SINA",
                    false,
                    state,
                    0,
                    null,
                    null,
                    null,
                    false,
                    List.of(
                        new CapabilityView(
                            MarketCapability.STOCK_DAILY,
                            true,
                            state,
                            null,
                            null,
                            null,
                            null,
                            FailureDiagnostics.code(state))))));
  }

  @Test
  void deniesOrdinaryUsersAndInvalidDatesBeforeUsingProvider() {
    assertThrows(BusinessException.class, () -> service.test(request()));
    try (var ignored = TenantContext.bind(new UserView(8, "reader", "USER"))) {
      assertThrows(BusinessException.class, () -> service.test(request()));
    }
    try (var ignored = TenantContext.bind(ADMIN)) {
      var future = request();
      future.setDate(DATE.plusDays(2));
      assertThrows(IllegalArgumentException.class, () -> service.test(future));
      var broad = request();
      broad.setCapability(MarketCapability.MARKET_SNAPSHOT);
      assertThrows(IllegalArgumentException.class, () -> service.test(broad));
      assertThrows(IllegalArgumentException.class, () -> service.test(new ProviderProbeRequest()));
    }
    verifyNoInteractions(provider);
  }

  @Test
  void keepsActualSummaryPropagatesIdentityAndLimitsRepeatedAttempts() {
    status("NOT_CHECKED");
    when(provider.probe(any()))
        .thenAnswer(
            invocation -> {
              assertEquals(ADMIN, TenantContext.user());
              return new ProviderProbeSample("600000.SH", 1, DATE, "SINA", null);
            });
    try (var ignored = TenantContext.bind(ADMIN)) {
      var first = service.test(request());
      assertEquals("SUCCESS", first.status());
      assertEquals("SINA", first.sample().source());
      assertEquals(DATE, first.sample().dataDate());
      assertEquals(CLOCK.instant().plusSeconds(60), first.retryAt());
      var repeated = service.test(request());
      assertEquals("PROBE_RATE_LIMITED", repeated.diagnostic().code());
      assertEquals("SKIPPED", repeated.status());
    }
    verify(provider, times(1)).probe(any());
  }

  @Test
  void protectedChannelReturnsOriginalDiagnosisWithoutCallingUpstream() {
    status("ACCESS_RESTRICTED");
    try (var ignored = TenantContext.bind(ADMIN)) {
      var result = service.test(request());
      assertEquals("SKIPPED", result.status());
      assertEquals("ACCESS_RESTRICTED", result.diagnostic().code());
      assertNull(result.sample());
    }
    verify(provider, never()).probe(any());
  }

  @Test
  void unsupportedUnconfiguredAndDemoDoNotUseRealRequests() {
    when(provider.status()).thenReturn(List.of());
    try (var ignored = TenantContext.bind(ADMIN)) {
      assertEquals("NOT_CONFIGURED", service.test(request()).diagnostic().code());
    }
    var other = new ProviderProbeServiceImpl(provider, CLOCK);
    try (var ignored = TenantContext.bind(ADMIN)) {
      status("NOT_CHECKED");
      when(provider.synthetic()).thenReturn(true);
      assertEquals("DEMO", other.test(request()).diagnostic().code());
    } finally {
      other.close();
    }
    other = new ProviderProbeServiceImpl(provider, CLOCK);
    try (var ignored = TenantContext.bind(ADMIN)) {
      when(provider.synthetic()).thenReturn(false);
      when(provider.status())
          .thenReturn(
              List.of(
                  new ProviderChannelView(
                      "SINA",
                      false,
                      "NOT_CHECKED",
                      0,
                      null,
                      null,
                      null,
                      false,
                      List.of(
                          new CapabilityView(
                              MarketCapability.STOCK_DAILY,
                              false,
                              "UNSUPPORTED",
                              null,
                              null,
                              null,
                              null,
                              null)))));
      assertEquals("UNSUPPORTED", other.test(request()).diagnostic().code());
    } finally {
      other.close();
    }
    verify(provider, never()).probe(any());
  }

  @Test
  void failuresNeverBecomeZeroRecordSuccessOrExposeRawSecrets() {
    status("NOT_CHECKED");
    when(provider.probe(any())).thenThrow(new IllegalStateException("secretToken=do-not-expose"));
    try (var ignored = TenantContext.bind(ADMIN)) {
      var result = service.test(request());
      assertEquals("FAILED", result.status());
      assertNull(result.sample());
      assertFalse(result.diagnostic().toString().contains("do-not-expose"));
    }
  }

  @Test
  void timeoutDoesNotAdmitAnotherTaskUntilTheOldInvocationActuallyExits() throws Exception {
    status("NOT_CHECKED");
    var release = new CountDownLatch(1);
    var exited = new CountDownLatch(1);
    var interrupted = new AtomicBoolean();
    when(provider.probe(any()))
        .thenAnswer(
            invocation -> {
              try {
                while (release.getCount() > 0) {
                  try {
                    release.await(20, TimeUnit.MILLISECONDS);
                  } catch (InterruptedException failure) {
                    interrupted.set(true);
                  }
                }
                return new ProviderProbeSample("600000.SH", 1, DATE, "SINA", null);
              } finally {
                exited.countDown();
              }
            });
    try (var ignored = TenantContext.bind(ADMIN)) {
      assertEquals("TIMED_OUT", service.test(request()).status());
      assertEquals(
          "PROBE_BUSY",
          service
              .test(new ProviderProbeRequest("EASTMONEY", MarketCapability.INDEX_DAILY, DATE))
              .diagnostic()
              .code());
      release.countDown();
      assertTrue(exited.await(2, TimeUnit.SECONDS));
      assertTrue(interrupted.get());
    } finally {
      release.countDown();
    }
    verify(provider, times(1)).probe(any());
  }
}
