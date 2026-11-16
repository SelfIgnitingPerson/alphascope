package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.calculator.ScopedMarketScoreCalculator;
import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.dto.ScopedScoreInput;
import com.alphascope.market.dto.ScoreCoverage;
import com.alphascope.market.dto.ThemeScoreEvidence;
import com.alphascope.market.vo.MarketObservation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 五维公式、范围门槛、实际分母与非有限值回归，不依赖真实上游。
 *
 * @author chenxuefeng
 */
class ScopedScoreTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);

  @Test
  void allFiveQualifiedDimensionsCanProduceTheFullScore() {
    var value = ScopedMarketScoreCalculator.calculate(input("FULL_A", false));
    assertEquals(100d, value.score());
    assertEquals("STRONG", value.state());
    assertTrue(
        value.dimensions().values().stream().allMatch(row -> row.status().equals("AVAILABLE")));
  }

  @Test
  void regionalAndFixedSampleScoresCannotEnterFullMarketTotal() {
    var value = ScopedMarketScoreCalculator.calculate(input("SH_SZ_A", false));
    assertNull(value.score());
    assertEquals("UNKNOWN", value.state());
    assertEquals(20d, value.dimensions().get("breadth").score());
    assertEquals("PROXY", value.dimensions().get("breadth").status());
    assertEquals("PROXY", value.dimensions().get("themeRelativeStrength").status());
  }

  @Test
  void syntheticCannotProduceARealMarketRating() {
    assertNull(ScopedMarketScoreCalculator.calculate(input("FULL_A", true)).score());
  }

  @Test
  void unknownRosterAndWrongDateRemainUnknown() {
    var ready = input("FULL_A", false);
    var value =
        ScopedMarketScoreCalculator.calculate(
            new ScopedScoreInput(
                date.plusDays(1),
                new ScoreCoverage("FULL_A", 100, 100, false),
                false,
                ready.observation(),
                ready.themes(),
                ready.leaders()));
    assertNull(value.score());
    assertTrue(value.dimensions().values().stream().allMatch(row -> row.score() == null));
  }

  @Test
  void missingDimensionCannotBeRescaledToOneHundred() {
    var ready = input("FULL_A", false);
    var value =
        ScopedMarketScoreCalculator.calculate(
            new ScopedScoreInput(
                date, ready.universe(), false, ready.observation(), ready.themes(), null));
    assertNull(value.score());
    assertNull(value.dimensions().get("leaderConfirmation").score());
  }

  private ScopedScoreInput input(String scope, boolean synthetic) {
    var trend =
        new MarketObservation.Trend("AVAILABLE", "up", 120d, 110d, 100d, 1d, .02, "fixture");
    var turnover =
        new MarketObservation.Turnover(
            "AVAILABLE", "up", 130d, 100d, 100d, 1.3, 1.3, 20, "fixture");
    var breadth =
        new MarketObservation.Breadth(
            "AVAILABLE", "up", 100, 0, 100, 100, 1d, 1d, 100, 100, 1d, 1d, 1d, "fixture");
    var observation =
        new MarketObservation(
            "TEST", date, scope, "AVAILABLE", trend, turnover, breadth, Map.of(), List.of());
    var coverage = new ScoreCoverage(scope, 100, 100, true);
    return new ScopedScoreInput(
        date,
        new ScoreCoverage("FULL_A", 100, 100, true),
        synthetic,
        observation,
        new ThemeScoreEvidence(date, coverage, 1d, List.of("A", "B")),
        new LeaderScoreEvidence(date, "AVAILABLE", coverage, List.of("A", "B"), 2, .01, "fixture"));
  }
}
