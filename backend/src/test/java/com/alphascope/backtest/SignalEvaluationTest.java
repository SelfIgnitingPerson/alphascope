package com.alphascope.backtest;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.vo.BacktestView;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.signal.entity.SignalEvent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 成熟窗口、时点、版本、基准及时间留出隔离的纯统计回归，不使用真实通道或当前数据库。
 *
 * @author chenxuefeng
 */
class SignalEvaluationTest {
  private final List<LocalDate> dates = new ArrayList<>();
  private final List<MarketBarDO> prices = new ArrayList<>();
  private final List<MarketBarDO> benchmark = new ArrayList<>();
  private final List<SignalEvent> events = new ArrayList<>();
  private final SignalStatisticsRequest request;

  SignalEvaluationTest() {
    for (LocalDate day = LocalDate.of(2024, 1, 2); dates.size() < 35; day = day.plusDays(1)) {
      if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY)
        dates.add(day);
    }
    for (int i = 0; i < dates.size(); i++) {
      prices.add(bar(dates.get(i), 100 + i));
      benchmark.add(bar(dates.get(i), 200 + i * .5));
    }
    request =
        SignalStatisticsRequest.builder()
            .symbol("000001.SZ")
            .start(dates.getFirst())
            .end(dates.getLast())
            .splitDate(dates.get(20))
            .build();
  }

  @Test
  void untriggeredRecordsAreNotObservedReturnSamples() {
    var event = event(0);
    event.setStatus("NOT_TRIGGERED");
    BacktestView value = evaluate();
    assertEquals("NOT_TRIGGERED", value.dataStatus());
    var row = value.statistics().get("5D").get("RULE_A");
    assertEquals("NO_TRIGGERED_SIGNALS", row.dataStatus());
    assertNull(row.meanReturn());
    assertEquals(1, row.exclusions().get("NOT_TRIGGERED"));
  }

  @Test
  void pendingWindowsRemainInsufficientEvenWhenSignalsExist() {
    event(34);
    var value = evaluate();
    assertEquals("INSUFFICIENT_DATA", value.dataStatus());
    assertEquals("WAITING_WINDOW", value.statistics().get("5D").get("RULE_A").dataStatus());
    assertEquals(0, value.statistics().get("5D").get("RULE_A").sampleCount());
  }

  @Test
  void matureSamplesHaveAlignedBenchmarkAndSmallSampleWarning() {
    event(0);
    var value = evaluate();
    var row = value.statistics().get("5D").get("RULE_A");
    assertEquals("OBSERVED", value.dataStatus());
    assertEquals("SMALL_SAMPLE", row.dataStatus());
    assertEquals(.05, row.meanReturn(), 1e-12);
    assertEquals(.0125, row.meanBenchmarkReturn(), 1e-12);
    assertEquals(.0375, row.meanExcessReturn(), 1e-12);
    assertEquals(1, row.benchmarkSampleCount());
    assertEquals(row.meanReturn(), row.medianReturn());
  }

  @Test
  void missingTradingDayIsNotReplacedByTheNextStoredBar() {
    event(0);
    prices.remove(2);
    var row = evaluate().statistics().get("5D").get("RULE_A");
    assertEquals(0, row.sampleCount());
    assertEquals(1, row.exclusions().get("PRICE_OR_FACTOR_GAP"));
  }

  @Test
  void overlappingEventsAreExcludedPerRuleAndHorizon() {
    event(0);
    event(1);
    event(6);
    var value = evaluate();
    var five = value.statistics().get("5D").get("RULE_A");
    assertEquals(2, five.sampleCount());
    assertEquals(1, five.exclusions().get("OVERLAPPING_SIGNAL"));
    assertEquals(1, value.statistics().get("10D").get("RULE_A").sampleCount());
    assertEquals(
        five.recordCount(),
        five.sampleCount() + five.exclusions().values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void strictModeRejectsBackfilledSignalsButRetrospectiveModeIsExplicit() {
    var event = event(0);
    event.setCreatedAt(dates.get(3).atTime(16, 0));
    assertEquals(1, evaluate().statistics().get("5D").get("RULE_A").sampleCount());
    request.setAvailabilityMode(AvailabilityMode.POINT_IN_TIME);
    var row = evaluate().statistics().get("5D").get("RULE_A");
    assertEquals(0, row.sampleCount());
    assertEquals(1, row.exclusions().get("SIGNAL_NOT_AVAILABLE_AS_OF"));
  }

  @Test
  void strictModeRejectsLaterRevisionsOfSignals() {
    var event = event(0);
    event.setUpdatedAt(dates.get(3).atTime(16, 0));
    request.setAvailabilityMode(AvailabilityMode.POINT_IN_TIME);
    assertEquals(0, evaluate().statistics().get("5D").get("RULE_A").sampleCount());
  }

  @Test
  void strictModeRejectsBackfilledOrRevisedPrices() {
    event(0);
    request.setAvailabilityMode(AvailabilityMode.POINT_IN_TIME);
    prices.get(2).setFetchedAt(dates.get(6).atTime(16, 0));
    var row = evaluate().statistics().get("5D").get("RULE_A");
    assertEquals(0, row.sampleCount());
    assertEquals(1, row.exclusions().get("PRICE_NOT_AVAILABLE_AS_OF"));
  }

  @Test
  void mixedVersionsDoNotGetSilentlyCombined() {
    event(0);
    event(6).setRuleVersion("V2");
    assertEquals(0, evaluate().statistics().get("5D").get("RULE_A").sampleCount());
    request.setRuleVersion("V1");
    var row = evaluate().statistics().get("5D").get("RULE_A");
    assertEquals(1, row.sampleCount());
    assertEquals(1, row.exclusions().get("OTHER_RULE_VERSION"));
  }

  @Test
  void earlyOutcomesCannotCrossIntoTheHoldoutInterval() {
    request.setSplitDate(dates.get(10));
    event(8);
    event(11);
    var windows = evaluate().validationWindows();
    assertEquals(0, windows.get(0).statistics().get("5D").get("RULE_A").sampleCount());
    assertEquals(1, windows.get(1).statistics().get("5D").get("RULE_A").sampleCount());
  }

  @Test
  void missingBenchmarkIsNotAZeroBenchmarkReturn() {
    event(0);
    benchmark.clear();
    var row = evaluate().statistics().get("5D").get("RULE_A");
    assertEquals(1, row.sampleCount());
    assertEquals(0, row.benchmarkSampleCount());
    assertNull(row.meanBenchmarkReturn());
    assertNull(row.meanExcessReturn());
  }

  @Test
  void missingCalendarCannotBeInferredFromStoredPrices() {
    event(0);
    var result = SignalEvaluationCalculator.evaluate(input(Set.of()));
    assertEquals("INSUFFICIENT_DATA", result.dataStatus());
    assertEquals(
        1, result.statistics().get("5D").get("RULE_A").exclusions().get("CALENDAR_MISSING"));
  }

  @Test
  void sameVersionNameWithDifferentFrozenParametersIsNotMixed() {
    var first = event(0);
    first.setEvidenceJson("{\"ruleParameters\":{\"threshold\":0.2}}");
    var second = event(10);
    second.setEvidenceJson("{\"ruleParameters\":{\"threshold\":0.3}}");
    var row = evaluate().statistics().get("5D").get("RULE_A");
    assertEquals(0, row.sampleCount());
    assertEquals(2, row.ruleVersions().size());
    assertEquals(2, row.exclusions().get("MIXED_RULE_VERSIONS"));
  }

  private BacktestView evaluate() {
    return SignalEvaluationCalculator.evaluate(input(new TreeSet<>(dates)));
  }

  private SignalEvaluationInput input(Set<LocalDate> calendar) {
    return SignalEvaluationInput.builder()
        .request(request)
        .bars(prices)
        .benchmark(benchmark)
        .signals(events)
        .tradingDates(calendar)
        .minimumSamples(20)
        .build();
  }

  private SignalEvent event(int index) {
    var row = new SignalEvent();
    row.setId((long) events.size() + 1);
    row.setRevisionId(row.getId());
    row.setTradeDate(dates.get(index));
    row.setRuleCode("RULE_A");
    row.setRuleVersion("V1");
    row.setStatus("TRIGGERED");
    row.setCreatedAt(dates.get(index).atTime(16, 10));
    row.setUpdatedAt(row.getCreatedAt());
    events.add(row);
    return row;
  }

  private MarketBarDO bar(LocalDate date, double close) {
    var row = new MarketBarDO();
    row.setTradeDate(date);
    row.setRevisionId(100L);
    row.setClose(BigDecimal.valueOf(close));
    row.setFactor(BigDecimal.ONE);
    row.setSynthetic(false);
    row.setFetchedAt(date.atTime(16, 0));
    row.setUpdatedAt(row.getFetchedAt());
    return row;
  }
}
