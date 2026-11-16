package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.MarketBarQuery;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.policy.MarketCachePolicy;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.*;
import com.alphascope.market.service.impl.MarketServiceImpl;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 已存真实历史的离线复用、缺口拒绝及原采集时间保留的验证。
 *
 * @author chenxuefeng
 */
class HistoricalReplayTest {
  final LocalDate start = LocalDate.of(2025, 1, 2), end = LocalDate.of(2025, 1, 6);
  final Set<LocalDate> expected = Set.of(start, start.plusDays(1), end);
  final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

  MarketBarDO row(LocalDate day) {
    var row = new MarketBarDO();
    row.setTradeDate(day);
    row.setSource("BAOSTOCK");
    row.setFactor(BigDecimal.valueOf(1d));
    row.setFetchedAt(now.minusDays(5));
    row.setSynthetic(false);
    row.setOpen(BigDecimal.valueOf(10d));
    row.setHigh(BigDecimal.valueOf(11d));
    row.setLow(BigDecimal.valueOf(9d));
    row.setClose(BigDecimal.valueOf(10d));
    row.setVolume(BigDecimal.valueOf(100d));
    row.setAmount(BigDecimal.valueOf(1000d));
    return row;
  }

  List<MarketBarDO> rows() {
    return new ArrayList<>(List.of(row(start), row(start.plusDays(1)), row(end)));
  }

  @Test
  void storedHistoryCanOutliveTtlWithoutChangingOriginalCollectionTimes() {
    var rows = rows();
    var before = rows.stream().map(Jsons::write).toList();
    assertFalse(MarketCachePolicy.usable(rows, expected, now, Duration.ofHours(24)));
    assertTrue(MarketCachePolicy.storedHistoricalUsable(rows, expected, now, end));
    assertEquals(before, rows.stream().map(Jsons::write).toList());
  }

  @Test
  void missingDayAndDuplicateDayAreNeverAccepted() {
    var rows = rows();
    rows.remove(1);
    assertFalse(MarketCachePolicy.storedHistoricalUsable(rows, expected, now, end));
    rows = rows();
    rows.add(row(start));
    assertFalse(MarketCachePolicy.storedHistoricalUsable(rows, expected, now, end));
  }

  @Test
  void invalidFactorPricesModeAndSourceAreRejected() {
    for (String key : List.of("factor", "high", "volume")) {
      var rows = rows();
      switch (key) {
        case "factor" -> rows.getFirst().setFactor(BigDecimal.valueOf(-1));
        case "high" -> rows.getFirst().setHigh(BigDecimal.valueOf(-1));
        case "volume" -> rows.getFirst().setVolume(BigDecimal.valueOf(-1));
        default -> fail("未知测试字段");
      }
      assertFalse(MarketCachePolicy.storedHistoricalUsable(rows, expected, now, end));
    }
    var demo = rows();
    demo.get(0).setSynthetic(true);
    assertFalse(MarketCachePolicy.storedHistoricalUsable(demo, expected, now, end));
    var mixed = rows();
    mixed.get(0).setSource("SINA");
    assertFalse(MarketCachePolicy.storedHistoricalUsable(mixed, expected, now, end));
  }

  @Test
  void currentOrFutureBusinessDatesAndFutureFetchedTimesAreNotReplayed() {
    assertFalse(MarketCachePolicy.storedHistoricalUsable(rows(), expected, now, now.toLocalDate()));
    assertFalse(
        MarketCachePolicy.storedHistoricalUsable(
            rows(), expected, now, now.toLocalDate().plusDays(1)));
    var rows = rows();
    rows.get(0).setFetchedAt(now.plusSeconds(1));
    assertFalse(MarketCachePolicy.storedHistoricalUsable(rows, expected, now, end));
  }

  /**
   * 历史回放测试的服务、模拟通道、写入服务及采集记录依赖。
   *
   * @author chenxuefeng
   */
  record Fixture(
      MarketService service,
      MarketDataProvider provider,
      MarketWriteService writer,
      ResearchCollectionMapper collections) {}

  Fixture fixture(List<MarketBarDO> rows) {
    var mapper = mock(MarketMapper.class);
    var provider = mock(MarketDataProvider.class);
    var writer = mock(MarketWriteService.class);
    var collections = mock(ResearchCollectionMapper.class);
    when(mapper.instrument("000300.SH")).thenReturn(fixtureInstrument1());
    when(mapper.latestSourceAt(1L, false, end)).thenReturn("BAOSTOCK");
    when(provider.acceptsSource("BAOSTOCK")).thenReturn(true);
    when(mapper.bars(
            MarketBarQuery.builder()
                .symbol("000300.SH")
                .startDate(start)
                .endDate(end)
                .synthetic(false)
                .source("BAOSTOCK")
                .build()))
        .thenReturn(rows);
    when(mapper.calendarDays(start, end, false)).thenReturn(5L);
    when(mapper.openDates(start, end, false)).thenReturn(expected.stream().sorted().toList());
    when(provider.fetchWindow("000300.SH", start, end, true))
        .thenThrow(new IllegalStateException("UPSTREAM_UNAVAILABLE"));
    return new Fixture(
        new MarketServiceImpl(
            mapper,
            provider,
            writer,
            collections,
            mock(ResearchDataRouter.class),
            new AlphaScopeProperties()),
        provider,
        writer,
        collections);
  }

  @Test
  void historicalReplayNeedsNoProviderFetchOrBarRewrite() {
    var f = fixture(rows());
    assertEquals(3, f.service().sync("000300.SH", start, end));
    verify(f.provider(), never()).fetchWindow(anyString(), any(), any(), anyBoolean());
    verifyNoInteractions(f.writer());
    var collection = org.mockito.ArgumentCaptor.forClass(ResearchCollection.class);
    verify(f.collections()).collectionSave(collection.capture());
    assertEquals("MARKET_WINDOW", collection.getValue().getDataKind());
    assertEquals("000300.SH", collection.getValue().getScope());
    assertEquals(end, collection.getValue().getBusinessDate());
    assertEquals("STORED_HISTORY", collection.getValue().getStatus());
    assertTrue(collection.getValue().getEvidenceJson().contains("原始采集时间"));
  }

  @Test
  void explicitRefreshStillForcesFetchAndNamesTheFailingInstrument() {
    var f = fixture(rows());
    ReflectionTestUtils.setField(f.service(), "cacheMinutes", 0L);
    var error =
        assertThrows(
            com.alphascope.common.exception.BusinessException.class,
            () -> f.service().sync("000300.SH", start, end));
    assertTrue(error.getMessage().contains("000300.SH"));
    assertTrue(error.getCause().getMessage().contains("UPSTREAM_UNAVAILABLE"));
    assertFalse(error.getMessage().contains("UPSTREAM_UNAVAILABLE"));
    assertEquals(com.alphascope.common.response.ResultCode.DATA_UNAVAILABLE, error.getCode());
    verify(f.provider()).fetchWindow("000300.SH", start, end, true);
    verifyNoInteractions(f.writer());
  }

  @Test
  void incompleteStoredHistoryStillFailsInsteadOfBeingUsedSilently() {
    var rows = rows();
    rows.remove(1);
    var f = fixture(rows);
    var error =
        assertThrows(
            com.alphascope.common.exception.BusinessException.class,
            () -> f.service().sync("000300.SH", start, end));
    assertTrue(error.getMessage().contains(start.plusDays(1).toString()));
    assertTrue(error.getMessage().contains("缺少"));
    verifyNoInteractions(f.writer());
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private Instrument fixtureInstrument1() {
    var row = new Instrument();
    row.setId(1L);
    row.setSymbol("000300.SH");
    row.setName("沪深300");
    row.setExchange("SH");
    row.setType("INDEX");
    row.setActive(true);
    return row;
  }
}
