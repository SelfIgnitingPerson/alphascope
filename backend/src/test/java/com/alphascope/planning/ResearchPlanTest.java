package com.alphascope.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.util.Jsons;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.service.MarketService;
import com.alphascope.planning.dto.PlanEvidence;
import com.alphascope.planning.dto.PlanReviewInput;
import com.alphascope.planning.entity.ResearchPlan;
import com.alphascope.planning.mapper.ResearchPlanMapper;
import com.alphascope.planning.service.impl.ResearchPlanServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 计划证据、原风险线与来源变化的回归；不接触业务库。
 *
 * @author chenxuefeng
 */
class ResearchPlanTest {
  private final ResearchPlanMapper mapper = mock(ResearchPlanMapper.class);
  private final HoldingResearchService research = mock(HoldingResearchService.class);
  private final MarketService market = mock(MarketService.class);
  private final ResearchPlanServiceImpl service =
      new ResearchPlanServiceImpl(mapper, research, market);
  private final LocalDate start = LocalDate.of(2026, 10, 30), end = LocalDate.of(2026, 11, 16);

  private HoldingResearchView view(LocalDate date, Double close, String source) {
    var action =
        HoldingResearchView.Action.builder()
            .code("OPEN_PULLBACK")
            .title("测试方案")
            .status("WAIT")
            .stopReference(90.0)
            .conditions(List.of())
            .build();
    var horizon =
        HoldingResearchView.Horizon.builder()
            .code("SHORT")
            .status("WAIT")
            .actions(List.of(action))
            .build();
    return HoldingResearchView.builder()
        .version("TEST_V1")
        .symbol("600000.SH")
        .asOf(date)
        .quoteDate(date)
        .close(close)
        .source(source)
        .horizons(List.of(horizon))
        .build();
  }

  private ResearchPlan seed() {
    var row = new ResearchPlan();
    row.setId(7L);
    row.setSymbol("600000.SH");
    row.setHorizon("SHORT");
    row.setActionCode("OPEN_PULLBACK");
    row.setSnapshotJson(Jsons.write(new PlanEvidence(view(start, 100.0, "TEST"), BigDecimal.ONE)));
    row.setSnapshotHash(Jsons.hash(row.getSnapshotJson()));
    when(mapper.get(7L)).thenReturn(row);
    return row;
  }

  @Test
  void savingCapturesOnlyServerResearchAndAnchorFactor() {
    var captured = new java.util.concurrent.atomic.AtomicReference<ResearchPlan>();
    when(research.read(eq("600000.SH"), any())).thenReturn(view(start, 100.0, "TEST"));
    var bar = new MarketBarDO();
    bar.setTradeDate(start);
    bar.setSource("TEST");
    bar.setFactor(BigDecimal.ONE);
    when(market.bars(anyString(), any())).thenReturn(List.of(bar));
    doAnswer(
            inv -> {
              ResearchPlan row = inv.getArgument(0);
              row.setId(9L);
              captured.set(row);
              return null;
            })
        .when(mapper)
        .insert(any());
    when(mapper.get(9L)).thenAnswer(inv -> captured.get());
    var saved =
        service.create(
            new com.alphascope.planning.dto.PlanCreateInput(
                "600000.SH", start, "SHORT", "OPEN_PULLBACK"));
    assertEquals(100.0, saved.research().close());
    assertEquals("TEST", saved.research().source());
    assertEquals(
        BigDecimal.ONE,
        Jsons.read(captured.get().getSnapshotJson(), PlanEvidence.class).anchorFactor());
    assertEquals(Jsons.hash(captured.get().getSnapshotJson()), saved.snapshotHash());
  }

  @Test
  void originalFactorIsFrozenAndStopIsNotMovedDown() {
    var row = seed();
    when(research.read(eq("600000.SH"), any())).thenReturn(view(end, 44.0, "TEST"));
    var bar = new MarketBarDO();
    bar.setTradeDate(end);
    bar.setSource("TEST");
    bar.setFactor(BigDecimal.valueOf(2));
    when(market.bars(anyString(), any())).thenReturn(List.of(bar));
    var result = service.review(7, new PlanReviewInput(end));
    assertEquals(45.0, result.adjustedOriginalStop());
    assertEquals("ORIGINAL_STOP_BREACHED", result.status());
    verify(mapper).review(any());
    assertEquals(Jsons.hash(row.getSnapshotJson()), row.getSnapshotHash());
  }

  @Test
  void changedSourceAndMissingFactorsDoNotGuessTheOldStop() {
    seed();
    when(research.read(anyString(), any())).thenReturn(view(end, 44.0, "OTHER"));
    var changed = service.review(7, new PlanReviewInput(end));
    assertEquals("SOURCE_CHANGED", changed.status());
    assertNull(changed.adjustedOriginalStop());
    when(research.read(anyString(), any())).thenReturn(view(end, 44.0, "TEST"));
    when(market.bars(anyString(), any())).thenReturn(List.of());
    assertEquals("DATA_INSUFFICIENT", service.review(7, new PlanReviewInput(end)).status());
  }

  @Test
  void missingOtherTenantPlanAndTamperedEvidenceAreRejected() {
    assertThrows(
        NotFoundException.class,
        () -> service.reviews(99, new com.alphascope.common.dto.PageRequest()));
    var row = seed();
    row.setSnapshotHash("bad");
    assertThrows(IllegalStateException.class, () -> service.review(7, new PlanReviewInput(end)));
    verify(mapper, never()).review(any());
  }

  @Test
  void archivedAndEarlierReviewCannotAppend() {
    var row = seed();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.review(7, new PlanReviewInput(start.minusDays(1))));
    row.setArchived(true);
    assertThrows(IllegalArgumentException.class, () -> service.review(7, new PlanReviewInput(end)));
    verify(mapper, never()).review(any());
  }

  @Test
  void sameSummaryStatusStillDetectsChangedIndividualCondition() {
    var row = seed();
    var before = view(start, 100.0, "TEST");
    var first = before.horizons().getFirst().actions().getFirst();
    var oldAction =
        HoldingResearchView.Action.builder()
            .code(first.code())
            .status("WAIT")
            .stopReference(90.0)
            .conditions(
                List.of(
                    new com.alphascope.intelligence.vo.PositionStrategy.Condition(
                        "量能", "旧实测", "门槛", false)))
            .build();
    var oldView =
        before.toBuilder()
            .horizons(
                List.of(
                    HoldingResearchView.Horizon.builder()
                        .code("SHORT")
                        .status("WAIT")
                        .actions(List.of(oldAction))
                        .build()))
            .build();
    row.setSnapshotJson(Jsons.write(new PlanEvidence(oldView, BigDecimal.ONE)));
    row.setSnapshotHash(Jsons.hash(row.getSnapshotJson()));
    var changed =
        HoldingResearchView.Action.builder()
            .code(first.code())
            .status("WAIT")
            .stopReference(90.0)
            .conditions(
                List.of(
                    new com.alphascope.intelligence.vo.PositionStrategy.Condition(
                        "量能", "新实测", "门槛", true)))
            .build();
    when(research.read(anyString(), any()))
        .thenReturn(
            view(end, 100.0, "TEST").toBuilder()
                .horizons(
                    List.of(
                        HoldingResearchView.Horizon.builder()
                            .code("SHORT")
                            .status("WAIT")
                            .actions(List.of(changed))
                            .build()))
                .build());
    var bar = new MarketBarDO();
    bar.setTradeDate(end);
    bar.setSource("TEST");
    bar.setFactor(BigDecimal.ONE);
    when(market.bars(anyString(), any())).thenReturn(List.of(bar));
    assertEquals("CONDITIONS_CHANGED", service.review(7, new PlanReviewInput(end)).status());
  }

  @Test
  void intradayOriginalStopTouchRemainsVisibleEvenAfterCloseRecovers() {
    seed();
    when(research.read(anyString(), any())).thenReturn(view(end, 105.0, "TEST"));
    var breach = new MarketBarDO();
    breach.setTradeDate(end.minusDays(1));
    breach.setSource("TEST");
    breach.setLow(BigDecimal.valueOf(89));
    breach.setFactor(BigDecimal.ONE);
    var last = new MarketBarDO();
    last.setTradeDate(end);
    last.setSource("TEST");
    last.setLow(BigDecimal.valueOf(100));
    last.setFactor(BigDecimal.ONE);
    when(market.bars(anyString(), any())).thenReturn(List.of(breach, last));
    var result = service.review(7, new PlanReviewInput(end));
    assertEquals("ORIGINAL_STOP_BREACHED", result.status());
    assertTrue(result.explanation().contains(end.minusDays(1).toString()));
    assertTrue(result.explanation().contains("收盘收复"));
  }
}
