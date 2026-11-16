package com.alphascope.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.calculator.TechnicalRuleReplayCalculator;
import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.vo.MarketScoreView;
import com.alphascope.signal.rule.RiskRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 核验回顾重建无前视、同源及复权边界，并检查全A验证不把缺项或旧模型当有效配对。
 *
 * @author chenxuefeng
 */
class ResearchValidationTest {
  private final List<LocalDate> dates = new ArrayList<>();
  private final List<MarketBarDO> prices = new ArrayList<>();
  private final RiskRules rules = new RiskRules(-.03, 1.5, -.10, .20, .05, .03, .10);

  ResearchValidationTest() {
    for (int i = 0; i < 100; i++) {
      dates.add(LocalDate.of(2025, 1, 1).plusDays(i));
      prices.add(bar(i, 100 + i));
    }
  }

  @Test
  void futurePriceChangesDoNotChangePastSignals() {
    var before = replay(prices);
    var changed =
        prices.stream().map(row -> Jsons.read(Jsons.write(row), MarketBarDO.class)).toList();
    for (int i = 80; i < changed.size(); i++) changed.get(i).setClose(BigDecimal.valueOf(1000 + i));
    var after = replay(changed);
    var cutoff = dates.get(79);
    assertEquals(
        Jsons.write(
            before.signals().stream().filter(row -> !row.getTradeDate().isAfter(cutoff)).toList()),
        Jsons.write(
            after.signals().stream().filter(row -> !row.getTradeDate().isAfter(cutoff)).toList()));
    assertTrue(
        before.signals().stream()
            .allMatch(row -> row.getCreatedAt() == null && row.getRevisionId() == null));
  }

  @Test
  void corporateActionDoesNotCreateFalseBreakAndFrozenInputRoundTrips() {
    var adjusted = new ArrayList<MarketBarDO>();
    for (int i = 0; i < dates.size(); i++) {
      var row = bar(i, i < 70 ? 100 : 50);
      row.setFactor(BigDecimal.valueOf(i < 70 ? 1 : 2));
      adjusted.add(row);
    }
    var reconstructed = replay(adjusted);
    assertFalse(
        reconstructed.signals().stream().anyMatch(row -> "TRIGGERED".equals(row.getStatus())));
    var restored = Jsons.read(Jsons.write(reconstructed), SignalEvaluationInput.class);
    assertEquals(reconstructed.tradingDates(), restored.tradingDates());
    assertEquals(
        Jsons.write(SignalEvaluationCalculator.evaluate(reconstructed)),
        Jsons.write(SignalEvaluationCalculator.evaluate(restored)));
  }

  @Test
  void calendarGapDoesNotBecomeAdjacentHistory() {
    var missing = new ArrayList<>(prices);
    missing.remove(65);
    var result = replay(missing);
    assertTrue(
        result.signals().stream()
            .filter(row -> row.getTradeDate().equals(dates.get(70)))
            .allMatch(row -> row.getStatus().equals("INSUFFICIENT_DATA")));
  }

  @Test
  void reconstructionRejectsSyntheticMixedSourcesAndStrictMode() {
    var changed = new ArrayList<>(prices);
    var mixed = bar(99, 199);
    mixed.setSource("OTHER");
    changed.set(99, mixed);
    assertThrows(IllegalArgumentException.class, () -> replay(changed));
    mixed.setSource("TEST");
    mixed.setSynthetic(true);
    assertThrows(IllegalArgumentException.class, () -> replay(changed));
    var input = input(prices);
    input.request().setAvailabilityMode(AvailabilityMode.POINT_IN_TIME);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TechnicalRuleReplayCalculator.reconstruct(
                new TechnicalReplayInput(input, rules.snapshot(), input.tradingDates())));
  }

  @Test
  void absentOrLegacyMarketScoresCannotEstablishEffectiveness() {
    var old = score(0, 80, "MARKET_V2");
    var result = MarketScoreValidationCalculator.evaluate(market(List.of(old)));
    assertEquals(6, result.size());
    assertTrue(
        result.stream()
            .allMatch(
                row ->
                    row.sampleCount() == 0
                        && row.status().equals("NO_VALID_SCORES")
                        && row.rankCorrelation() == null));
  }

  @Test
  void marketCorrelationIsComputedAndEarlyWindowsCannotCrossSplit() {
    var input =
        market(
            List.of(
                score(0, 20, "MARKET_V3"),
                score(6, 50, "MARKET_V3"),
                score(12, 80, "MARKET_V3"),
                score(68, 90, "MARKET_V3")));
    var result =
        MarketScoreValidationCalculator.evaluate(input).stream()
            .filter(row -> row.period().equals("EARLY") && row.horizon() == 5)
            .findFirst()
            .orElseThrow();
    assertEquals(3, result.sampleCount());
    assertEquals(-1, result.rankCorrelation(), 1e-12);
    assertTrue(result.highMinusLow() < 0);
    assertEquals("SMALL_SAMPLE", result.status());
    assertEquals(
        Jsons.write(MarketScoreValidationCalculator.evaluate(input)),
        Jsons.write(
            MarketScoreValidationCalculator.evaluate(
                Jsons.read(Jsons.write(input), MarketScoreValidationCalculator.Input.class))));
  }

  @Test
  void constantScoresHaveUndefinedRankCorrelationAndUnknownNeverQualifies() {
    var unknown = score(18, 50, "MARKET_V3");
    unknown =
        new MarketScoreView(
            unknown.id(),
            unknown.tradeDate(),
            unknown.modelVersion(),
            unknown.score(),
            "UNKNOWN",
            "{}",
            unknown.createdAt(),
            unknown.updatedAt(),
            false);
    var result =
        MarketScoreValidationCalculator.evaluate(
                market(
                    List.of(
                        score(0, 50, "MARKET_V3"),
                        score(6, 50, "MARKET_V3"),
                        score(12, 50, "MARKET_V3"),
                        unknown)))
            .stream()
            .filter(row -> row.period().equals("EARLY") && row.horizon() == 5)
            .findFirst()
            .orElseThrow();
    assertEquals(3, result.sampleCount());
    assertNull(result.rankCorrelation());
  }

  @Test
  void marketBenchmarkDoesNotCrossSources() {
    prices.get(3).setSource("OTHER");
    var result =
        MarketScoreValidationCalculator.evaluate(market(List.of(score(0, 80, "MARKET_V3"))));
    assertTrue(result.stream().allMatch(row -> row.sampleCount() == 0));
  }

  private SignalEvaluationInput replay(List<MarketBarDO> bars) {
    var source = input(bars);
    return TechnicalRuleReplayCalculator.reconstruct(
            new TechnicalReplayInput(source, rules.snapshot(), source.tradingDates()))
        .evaluation();
  }

  private SignalEvaluationInput input(List<MarketBarDO> bars) {
    return new SignalEvaluationInput(
        SignalStatisticsRequest.builder()
            .symbol("000001.SZ")
            .start(dates.getFirst())
            .end(dates.getLast())
            .build(),
        bars,
        prices,
        List.of(),
        new TreeSet<>(dates),
        20);
  }

  private MarketScoreValidationCalculator.Input market(List<MarketScoreView> scores) {
    return new MarketScoreValidationCalculator.Input(
        dates.getFirst(), dates.getLast(), scores, prices, new TreeSet<>(dates), 20);
  }

  private MarketScoreView score(int at, double score, String version) {
    return new MarketScoreView(
        (long) at + 1,
        dates.get(at),
        version,
        BigDecimal.valueOf(score),
        "POSITIVE",
        "{}",
        dates.get(at).atTime(17, 0),
        dates.get(at).atTime(17, 0),
        false);
  }

  private MarketBarDO bar(int at, double close) {
    var row = new MarketBarDO();
    row.setTradeDate(dates.get(at));
    row.setClose(BigDecimal.valueOf(close));
    row.setVolume(BigDecimal.valueOf(100));
    row.setFactor(BigDecimal.ONE);
    row.setSource("TEST");
    row.setSynthetic(false);
    return row;
  }
}
