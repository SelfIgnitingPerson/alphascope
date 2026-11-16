package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.policy.MarketCachePolicy;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketWriteService;
import com.alphascope.market.service.impl.MarketServiceImpl;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 同源缓存日期完整性、有效期及重复日期拒绝策略的验证。
 *
 * @author chenxuefeng
 */
class MarketCachePolicyTest {
  @Test
  void completeHistoricalCalendarCanBeUsedOfflineButAGapStillRequiresFetch() {
    var mapper = org.mockito.Mockito.mock(MarketMapper.class);
    var provider = org.mockito.Mockito.mock(MarketDataProvider.class);
    var service =
        new MarketServiceImpl(
            mapper,
            provider,
            org.mockito.Mockito.mock(MarketWriteService.class),
            org.mockito.Mockito.mock(ResearchCollectionMapper.class),
            mock(ResearchDataRouter.class),
            new AlphaScopeProperties());
    org.mockito.Mockito.when(mapper.calendarDays(first, last, false)).thenReturn(5L);
    service.syncCalendar(first, last);
    org.mockito.Mockito.verify(provider, org.mockito.Mockito.never())
        .fetchCalendar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.when(mapper.calendarDays(first, last, false)).thenReturn(4L);
    org.mockito.Mockito.when(provider.fetchCalendar(first, last)).thenReturn(Map.of());
    service.syncCalendar(first, last);
    org.mockito.Mockito.verify(provider).fetchCalendar(first, last);
  }

  LocalDate first = LocalDate.of(2025, 1, 2), last = LocalDate.of(2025, 1, 6);
  LocalDateTime now = last.atTime(17, 0);

  MarketBarDO row(LocalDate d, String source, LocalDateTime time) {
    var row = new MarketBarDO();
    row.setTradeDate(d);
    row.setSource(source);
    row.setFactor(BigDecimal.ONE);
    row.setFetchedAt(time);
    return row;
  }

  boolean usable(List<MarketBarDO> rows, Set<LocalDate> expected) {
    return MarketCachePolicy.usable(rows, expected, now, Duration.ofHours(24));
  }

  @Test
  void completeTradingCalendarIgnoresWeekend() {
    assertTrue(
        usable(
            List.of(row(first, "B", now), row(first.plusDays(1), "B", now), row(last, "B", now)),
            Set.of(first, first.plusDays(1), last)));
  }

  @Test
  void missingMiddleDayFailsEvenWhenEndpointsExist() {
    assertFalse(
        usable(
            List.of(row(first, "B", now), row(last, "B", now)),
            Set.of(first, first.plusDays(1), last)));
  }

  @Test
  void staleMiddleRowCannotHideBehindFreshLastRow() {
    assertFalse(
        usable(
            List.of(row(first, "B", now.minusDays(2)), row(last, "B", now)), Set.of(first, last)));
  }

  @Test
  void suspendedDateCanBeRemovedOnlyByCallerEvidence() {
    assertTrue(usable(List.of(row(first, "B", now), row(last, "B", now)), Set.of(first, last)));
  }

  @Test
  void mixedSourcesFail() {
    assertFalse(usable(List.of(row(first, "B", now), row(last, "S", now)), Set.of(first, last)));
  }

  @Test
  void duplicateDatesFail() {
    assertFalse(usable(List.of(row(first, "B", now), row(first, "B", now)), Set.of(first)));
  }

  @Test
  void unknownCalendarIsNeverComplete() {
    assertFalse(usable(List.of(row(first, "B", now)), Set.of()));
  }

  @Test
  void zeroTtlForcesFetch() {
    assertFalse(
        MarketCachePolicy.usable(List.of(row(first, "B", now)), Set.of(first), now, Duration.ZERO));
  }

  @Test
  void inactiveChannelsExplainMissingConfigurationWithoutImplyingUpstreamRefusal() {
    var mapper = mock(MarketMapper.class);
    var provider = mock(MarketDataProvider.class);
    var research = mock(ResearchDataRouter.class);
    var properties = new AlphaScopeProperties();
    properties.setProvider(AlphaScopeProperties.ProviderType.MULTI);
    org.mockito.Mockito.when(provider.status())
        .thenReturn(
            List.of(
                new com.alphascope.market.vo.ProviderChannelView(
                    "SINA", false, "NOT_CHECKED", 0, null, "", null, false)));
    org.mockito.Mockito.when(research.status("SINA")).thenReturn(List.of());
    org.mockito.Mockito.when(mapper.latestSources(false)).thenReturn(List.of());
    var service =
        new MarketServiceImpl(
            mapper,
            provider,
            mock(MarketWriteService.class),
            mock(ResearchCollectionMapper.class),
            research,
            properties);
    var first =
        service.providerStatus().inactiveChannels().stream()
            .filter(row -> row.channel().equals("TUSHARE"))
            .findFirst()
            .orElseThrow();
    assertEquals("NOT_CONFIGURED", first.state());
    assertTrue(first.reason().contains("未请求上游"));
    properties.getMarket().setChannels(List.of(AlphaScopeProperties.ProviderType.SINA));
    service =
        new MarketServiceImpl(
            mapper,
            provider,
            mock(MarketWriteService.class),
            mock(ResearchCollectionMapper.class),
            research,
            properties);
    var second =
        service.providerStatus().inactiveChannels().stream()
            .filter(row -> row.channel().equals("TUSHARE"))
            .findFirst()
            .orElseThrow();
    assertEquals("DISABLED", second.state());
    org.mockito.Mockito.verify(provider, org.mockito.Mockito.never())
        .fetchCalendar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
