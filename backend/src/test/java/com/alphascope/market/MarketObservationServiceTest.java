package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.entity.MarketFactorSnapshot;
import com.alphascope.market.entity.MarketObservationSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.mapper.MarketObservationMapper;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.impl.MarketObservationServiceImpl;
import com.alphascope.market.vo.MarketBreadthView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 同源因子缓存、访问受限与取消传播的验证；全部研究通道路由隔离，不连接真实接口。
 *
 * @author chenxuefeng
 */
class MarketObservationServiceTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);
  private final MarketObservationMapper mapper = mock(MarketObservationMapper.class);
  private final MarketBreadthMapper snapshots = mock(MarketBreadthMapper.class);
  private final MarketBreadthService breadth = mock(MarketBreadthService.class);
  private final MarketService market = mock(MarketService.class);
  private final ResearchCollectionMapper collections = mock(ResearchCollectionMapper.class);
  private final ResearchDataRouter router = mock(ResearchDataRouter.class);
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void cachedFactorsKeepStableEvidenceWithoutUpstreamRequests() throws Exception {
    var service = service();
    when(mapper.factor(request("sh.600000"))).thenReturn(factor(2L, "sh.600000"));
    when(mapper.factor(request("sz.000001"))).thenReturn(factor(3L, "sz.000001"));
    service.sync(date);
    verify(router, never()).factors(any());
    verify(mapper, never()).factorSave(any());
    var row = ArgumentCaptor.forClass(MarketObservationSnapshot.class);
    verify(mapper).save(row.capture());
    var refs = json.readTree(row.getValue().getEvidenceJson()).path("sourceRefs");
    assertEquals(3, refs.size());
    assertEquals("sh.600000", refs.get(1).path("symbol").asText());
    assertEquals("BAOSTOCK", refs.get(1).path("source").asText());
    assertEquals(
        Jsons.hash(row.getValue().getAnalysisJson() + row.getValue().getEvidenceJson()),
        row.getValue().getContentHash());
  }

  @Test
  void accessRestrictionKeepsCachedFactorAndLeavesMissingFactorUnknown() throws Exception {
    var service = service();
    when(mapper.factor(request("sh.600000"))).thenReturn(factor(2L, "sh.600000"));
    when(router.factors(request("sz.000001")))
        .thenThrow(new IllegalStateException("ACCESS_RESTRICTED"));
    var guard = mock(BaoStockAccessGuard.class);
    when(guard.blocked()).thenReturn(true);
    try (var guards = mockStatic(BaoStockAccessGuard.class)) {
      guards.when(BaoStockAccessGuard::standard).thenReturn(guard);
      service.sync(date);
      verify(mapper, never()).factorSave(any());
      var row = ArgumentCaptor.forClass(MarketObservationSnapshot.class);
      verify(mapper).save(row.capture());
      assertEquals(2, json.readTree(row.getValue().getEvidenceJson()).path("sourceRefs").size());
      var progress = ArgumentCaptor.forClass(ResearchCollection.class);
      verify(collections, atLeastOnce()).collectionSave(progress.capture());
      assertEquals("ACCESS_RESTRICTED", progress.getValue().getStatus());
    }
  }

  @Test
  void interruptionStopsCollectionAndPreservesTheThreadFlag() {
    var service = service();
    Thread.currentThread().interrupt();
    try {
      assertThrows(TaskStoppedException.class, () -> service.sync(date));
      assertTrue(Thread.currentThread().isInterrupted());
      verify(mapper, never()).save(any());
      verify(router, never()).factors(any());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void unsupportedFactorCapabilityDoesNotIssueThousandsOfDoomedQueries() {
    var service = service();
    when(router.supports(eq("BAOSTOCK"), any())).thenReturn(false);
    service.sync(date);
    verify(router, never()).factors(any());
    verify(mapper, never()).factor(any());
    verify(mapper).save(any());
  }

  private MarketObservationServiceImpl service() {
    when(router.sources()).thenReturn(List.of("BAOSTOCK"));
    when(router.supports(eq("BAOSTOCK"), any())).thenReturn(true);
    when(market.expectedDates(any())).thenReturn(Set.of(date));
    when(market.bars(anyString(), any())).thenReturn(List.of());
    var current = new MarketBreadthView();
    current.setSource("BAOSTOCK");
    current.setStatus("COMPLETE");
    current.setBusinessDate(date);
    when(breadth.snapshot(date)).thenReturn(current);
    when(snapshots.forSource(any()))
        .thenReturn(
            MarketBreadthSnapshot.builder()
                .id(1L)
                .tradeDate(date)
                .source("BAOSTOCK")
                .contentHash("breadth-hash")
                .metricsJson(
                    "{\"status\":\"COMPLETE\",\"observed\":2,\"advancers\":1,\"suspended\":0,\"amount\":100}")
                .rawJson(
                    "{\"daily\":[{\"code\":\"sh.600000\",\"tradestatus\":\"1\",\"close\":10,\"date\":\"2026-10-30\"},{\"code\":\"sz.000001\",\"tradestatus\":\"1\",\"close\":20,\"date\":\"2026-10-30\"}]}")
                .build());
    return new MarketObservationServiceImpl(
        mapper, snapshots, breadth, market, collections, json, .98, 1.1, .9, .6, .4, router);
  }

  private ResearchFactorRequest request(String code) {
    return ResearchFactorRequest.builder()
        .symbol(code)
        .start(date)
        .end(date)
        .source("BAOSTOCK")
        .build();
  }

  private MarketFactorSnapshot factor(long id, String symbol) {
    return MarketFactorSnapshot.builder()
        .id(id)
        .symbol(symbol)
        .source("BAOSTOCK")
        .verifiedThrough(date)
        .contentHash("factor-" + symbol)
        .rawJson("[]")
        .build();
  }
}
