package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 测试路径与生产路径共用契约、许可和冷却，但禁止降级及替换生产日历。
 *
 * @author chenxuefeng
 */
class ProviderProbeRoutingTest {
  private static final LocalDate DATE = LocalDate.of(2026, 11, 12);

  /**
   * 计数型无外网供应商，用于证明指定来源没有被备用来源掩盖。
   *
   * @author chenxuefeng
   */
  private static final class Channel implements MarketDataProvider {
    private final String name;
    private int calls;
    private boolean fail;

    private Channel(String name) {
      this.name = name;
    }

    @Override
    public String code() {
      return name;
    }

    @Override
    public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate start, LocalDate end) {
      calls++;
      if (fail) throw new IllegalStateException("EMPTY_RESPONSE");
      return List.of(
          new DailyBarDto(
              symbol,
              DATE,
              BigDecimal.TEN,
              BigDecimal.TEN,
              BigDecimal.ONE,
              BigDecimal.TEN,
              BigDecimal.TEN,
              BigDecimal.TEN,
              BigDecimal.ONE,
              name,
              DATE.atTime(16, 0),
              false));
    }

    @Override
    public Map<LocalDate, Boolean> fetchCalendar(LocalDate start, LocalDate end) {
      return Map.of(start, true);
    }
  }

  @Test
  void selectedFailureNeverCallsOrSucceedsThroughAnotherProvider() {
    var primary = new Channel("SINA");
    primary.fail = true;
    var backup = new Channel("EASTMONEY");
    var router =
        new MultiChannelMarketDataProvider(
            List.of(primary, backup), Duration.ofSeconds(30), Clock.systemUTC());
    var request = new ProviderProbeRequest("SINA", MarketCapability.STOCK_DAILY, DATE);
    assertThrows(IllegalStateException.class, () -> router.probe(request));
    assertEquals(1, primary.calls);
    assertEquals(0, backup.calls);
    assertThrows(IllegalStateException.class, () -> router.probe(request));
    assertEquals(1, primary.calls, "测试也要服从原有能力冷却");
  }

  @Test
  void calendarTestDoesNotReplaceTheProductionCalendarSourceOrWindow() {
    var channel = new Channel("SINA");
    var router =
        new MultiChannelMarketDataProvider(
            List.of(channel), Duration.ofSeconds(30), Clock.systemUTC());
    var sample =
        router.probe(new ProviderProbeRequest("SINA", MarketCapability.TRADING_CALENDAR, DATE));
    assertEquals(Boolean.TRUE, sample.marketOpen());
    assertEquals("NONE", router.calendarSource());
    assertEquals(
        "AVAILABLE",
        router.status().getFirst().capabilities().stream()
            .filter(value -> value.capability() == MarketCapability.TRADING_CALENDAR)
            .findFirst()
            .orElseThrow()
            .state());
  }

  @Test
  void validSamplesKeepTheActualSourceAndFixedSymbol() {
    var channel = new Channel("SINA");
    var router =
        new MultiChannelMarketDataProvider(
            List.of(channel), Duration.ofSeconds(30), Clock.systemUTC());
    var sample = router.probe(new ProviderProbeRequest("SINA", MarketCapability.STOCK_DAILY, DATE));
    assertEquals("600000.SH", sample.symbol());
    assertEquals("SINA", sample.source());
    assertEquals(1, sample.records());
  }

  @Test
  void configuredLicenseWrapperStillBlocksProbeCollection() {
    var raw = new Channel("SINA");
    var license =
        org.mockito.Mockito.mock(com.alphascope.operations.service.SourceLicenseService.class);
    org.mockito.Mockito.doThrow(
            new com.alphascope.common.exception.BusinessException(
                com.alphascope.common.response.ResultCode.FORBIDDEN, 403, "未授权"))
        .when(license)
        .require("SINA", "COLLECT");
    var router =
        new MultiChannelMarketDataProvider(
            List.of(new LicensedMarketProvider(raw, license)),
            Duration.ofSeconds(30),
            Clock.systemUTC());
    assertThrows(
        IllegalStateException.class,
        () -> router.probe(new ProviderProbeRequest("SINA", MarketCapability.STOCK_DAILY, DATE)));
    assertEquals(0, raw.calls);
  }

  @Test
  void statusReadsDoNotWaitForANetworkProbe() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var data = new Channel("SINA");
    MarketDataProvider slow =
        new MarketDataProvider() {
          @Override
          public String code() {
            return "SINA";
          }

          @Override
          public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException("INTERRUPTED");
            }
            return data.fetchDailyBars(symbol, from, to);
          }

          @Override
          public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
            return Map.of(from, true);
          }
        };
    var router =
        new MultiChannelMarketDataProvider(
            List.of(slow), Duration.ofSeconds(30), Clock.systemUTC());
    try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var future =
          worker.submit(
              () ->
                  router.probe(
                      new ProviderProbeRequest("SINA", MarketCapability.STOCK_DAILY, DATE)));
      try {
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertTimeoutPreemptively(
            Duration.ofSeconds(1), () -> assertEquals(1, router.status().size()));
      } finally {
        release.countDown();
      }
      assertEquals("SINA", future.get(2, java.util.concurrent.TimeUnit.SECONDS).source());
    }
  }

  @Test
  void missingFactorsFailWithAContractCodeInsteadOfAnUnknownNullPointer() {
    var bar =
        new DailyBarDto(
            "600000.SH",
            DATE,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.TEN,
            null,
            "SINA",
            DATE.atTime(16, 0),
            false);
    var failure =
        assertThrows(
            IllegalStateException.class,
            () -> ProviderValidation.bars(List.of(bar), "600000.SH", DATE, DATE));
    assertEquals("INVALID_PRICE_OR_FACTOR", failure.getMessage());
  }
}
