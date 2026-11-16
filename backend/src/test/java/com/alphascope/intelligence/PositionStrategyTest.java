package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphascope.intelligence.calculator.PositionStrategyCalculator;
import com.alphascope.intelligence.dto.PositionStrategyInput;
import com.alphascope.intelligence.vo.PositionStrategy;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.intelligence.vo.PriceVolumeStudy.Zone;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 固定策略触发、追价与风险过滤、两次确认、个人前提和缺证据边界验证。
 *
 * @author chenxuefeng
 */
class PositionStrategyTest {
  private static final LocalDate DATE = LocalDate.of(2026, 11, 10);

  @Test
  void pullbackRequiresRecoveryTrendAndLimitedRiskRatherThanJustAnUntouchedStop() {
    var study =
        base().toBuilder().close(100.0).ma20(99.0).volumeRatio(0.8).stopReference(95.0).build();
    var plan = plan(input(study, 99, 98, .01), "OPEN_PULLBACK");
    assertEquals("CONDITIONAL_MATCH", plan.status());
    assertTrue(plan.conditions().stream().allMatch(c -> Boolean.TRUE.equals(c.met())));
    assertTrue(plan.referenceRisk() <= .08);
    assertTrue(plan.assumptions().stream().anyMatch(s -> s.contains("未核实是否空仓")));
    var weakTrend = study.toBuilder().ma20(105.0).build();
    assertEquals("WAIT", plan(input(weakTrend, 99, 98, .01), "OPEN_PULLBACK").status());
  }

  @Test
  void firstBreakoutNeedsVolumeAndDoesNotChaseBeyondThreePercent() {
    var study = base().toBuilder().close(102.0).volumeRatio(1.4).stopReference(97.0).build();
    var context = input(study, 99, 100, .03).toBuilder().breakoutReference(zone(98, 100)).build();
    assertEquals("CONDITIONAL_MATCH", plan(context, "OPEN_BREAKOUT").status());
    assertEquals(100.0, plan(context, "OPEN_BREAKOUT").observationZone().lower());
    assertEquals(103.0, plan(context, "OPEN_BREAKOUT").observationZone().upper());
    var thin = context.toBuilder().study(study.toBuilder().volumeRatio(.9).build()).build();
    assertEquals("WAIT", plan(thin, "OPEN_BREAKOUT").status());
    var chase = context.toBuilder().study(study.toBuilder().close(110.0).build()).build();
    assertEquals("WAIT", plan(chase, "OPEN_BREAKOUT").status());
  }

  @Test
  void addingNeedsTwoClosesAndRetestAndDoesNotAssumeThatTheUserHoldsTheStock() {
    var study =
        base().toBuilder()
            .close(102.0)
            .volumeRatio(1.2)
            .volumeRatio5(1.1)
            .stopReference(97.0)
            .build();
    var context = input(study, 101, 100.5, .01).toBuilder().addReference(zone(98, 100)).build();
    var matched = plan(context, "ADD_RETEST");
    assertEquals("CONDITIONAL_MATCH", matched.status());
    assertTrue(matched.assumptions().stream().anyMatch(s -> s.contains("未核实是否持有")));
    assertEquals(
        "WAIT", plan(context.toBuilder().previousClose(99).build(), "ADD_RETEST").status());
    assertEquals("WAIT", plan(context.toBuilder().currentLow(103).build(), "ADD_RETEST").status());
  }

  @Test
  void wideStructuralStopBlocksEntriesWithoutMovingTheStopCloser() {
    var study = base().toBuilder().stopReference(80.0).build();
    var plans = PositionStrategyCalculator.calculate(input(study, 99, 99, .01));
    for (var plan : plans.subList(0, 3)) {
      assertEquals("RISK_BLOCKED", plan.status());
      assertEquals(80.0, plan.stopReference());
      assertTrue(plan.referenceRisk() > plan.maximumReferenceRisk());
    }
  }

  @Test
  void riskLimitBoundaryIsInclusiveButABiggerDistanceIsNot() {
    var study = base().toBuilder().stopReference(94.76).build();
    var context = input(study, 99, 100, .01).toBuilder().breakoutReference(zone(98, 100)).build();
    assertEquals(.08, plan(context, "OPEN_BREAKOUT").referenceRisk());
    assertFalse(plan(context, "OPEN_BREAKOUT").status().equals("RISK_BLOCKED"));
    var wider = context.toBuilder().study(study.toBuilder().stopReference(94.75).build()).build();
    assertTrue(plan(wider, "OPEN_BREAKOUT").observationZone().upper() < 103);
    assertTrue(plan(wider, "OPEN_BREAKOUT").referenceRisk() <= .08);
    var outsideCappedBand =
        wider.toBuilder().study(wider.study().toBuilder().close(103.0).build()).build();
    assertEquals("WAIT", plan(outsideCappedBand, "OPEN_BREAKOUT").status());
  }

  @Test
  void aReductionTriggerBlocksOpeningAndAddingAndAnyRiskGroupIsSufficient() {
    var study =
        base().toBuilder().close(90.0).stopReference(95.0).decision("REDUCE_REVIEW").build();
    var plans = PositionStrategyCalculator.calculate(input(study, 100, 89, -.1));
    assertTrue(plans.subList(0, 3).stream().allMatch(p -> p.status().equals("RISK_BLOCKED")));
    var reduce = plans.getLast();
    assertEquals("ANY", reduce.conditionLogic());
    assertEquals("CONDITIONAL_MATCH", reduce.status());
    assertTrue(reduce.conditions().getFirst().met());
  }

  @Test
  void missingDataOrFutureReferencesNeverCreateTradablePlans() {
    var unavailable =
        base().toBuilder().status("INSUFFICIENT_DATA").missing(List.of("缺少交易日历")).build();
    var plans = PositionStrategyCalculator.calculate(input(unavailable, 100, 99, 0));
    assertEquals(4, plans.size());
    assertTrue(
        plans.stream()
            .allMatch(p -> p.status().equals("UNAVAILABLE") && p.observationZone() == null));
    assertNull(plans.getFirst().conditions().getFirst().met());
    var future = input(base(), 100, 99, 0).toBuilder().addReferenceAsOf(DATE).build();
    assertTrue(
        PositionStrategyCalculator.calculate(future).stream()
            .allMatch(p -> p.status().equals("UNAVAILABLE")));
  }

  @Test
  void noValidStopWithholdsEntryBandAndKeepsUnknownDistinctFromNotTriggered() {
    var plans =
        PositionStrategyCalculator.calculate(
            input(base().toBuilder().stopReference(null).build(), 100, 99, 0));
    assertTrue(
        plans.subList(0, 3).stream()
            .allMatch(p -> p.status().equals("UNAVAILABLE") && p.observationZone() == null));
    assertNull(plans.getLast().conditions().getFirst().met());
  }

  @Test
  void reductionUsesAuthoritativeFrozenFlagsInsteadOfRecomputingRoundedMetrics() {
    var study = base().toBuilder().close(100.0).ma20(100.0).decision("REDUCE_REVIEW").build();
    var context = input(study, 101, 99, -.01).toBuilder().weakSelling(true).build();
    assertEquals("CONDITIONAL_MATCH", plan(context, "REDUCE").status());
    assertTrue(plan(context, "REDUCE").conditions().get(1).met());
    assertEquals("RISK_BLOCKED", plan(context, "OPEN_PULLBACK").status());
  }

  private static PositionStrategy plan(PositionStrategyInput input, String code) {
    return PositionStrategyCalculator.calculate(input).stream()
        .filter(p -> p.code().equals(code))
        .findFirst()
        .orElseThrow();
  }

  private static PositionStrategyInput input(
      PriceVolumeStudy study, double previous, double low, double dayReturn) {
    return PositionStrategyInput.builder()
        .study(study)
        .previousClose(previous)
        .currentLow(low)
        .dayReturn(dayReturn)
        .stopTriggered(
            study.stopReference() == null ? null : study.close() <= study.stopReference())
        .weakSelling(
            (study.close() < study.support().lower() && dayReturn < 0 && study.volumeRatio() >= 1.2)
                || (study.return5() <= -.02
                    && study.volumeRatio5() >= 1.2
                    && study.close() < study.ma20()))
        .shrinkingRally(
            study.resistances().stream()
                    .anyMatch(z -> study.close() >= z.lower() * .98 && study.close() <= z.upper())
                && study.return5() >= .02
                && study.volumeRatio5() <= .8)
        .breakoutReference(zone(104, 106))
        .addReference(zone(102, 104))
        .breakoutReferenceAsOf(LocalDate.of(2026, 11, 9))
        .addReferenceAsOf(LocalDate.of(2026, 11, 6))
        .build();
  }

  private static PriceVolumeStudy base() {
    return PriceVolumeStudy.builder()
        .version("PRICE_VOLUME_2M_V3")
        .symbol("TEST.SZ")
        .name("测试")
        .status("AVAILABLE")
        .volumeBasis("TURNOVER_AMOUNT")
        .asOf(DATE)
        .close(102.0)
        .ma20(100.0)
        .volumeRatio(1.2)
        .volumeRatio5(1.1)
        .return5(.01)
        .stopReference(95.0)
        .stopStatus("ABOVE_REFERENCE")
        .support(zone(97, 99))
        .resistances(List.of(zone(104, 106)))
        .decision("OBSERVE")
        .conclusion("继续观察")
        .evidence(List.of())
        .missing(List.of())
        .methodNotes(List.of())
        .build();
  }

  private static Zone zone(double low, double high) {
    return new Zone(low, high, "CONFIRMED_SWING_HIGH", List.of(LocalDate.of(2026, 11, 5)), null);
  }
}
