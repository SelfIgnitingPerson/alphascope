package com.alphascope.backtest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.alphascope.backtest.calculator.PortfolioReplayCalculator;
import com.alphascope.backtest.calculator.StrategyReplayCalculator;
import com.alphascope.backtest.dto.PortfolioReplaySettings;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.intelligence.calculator.MediumTermResearchCalculator;
import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PositionStrategy;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证执行引擎的次日开盘、成本、风险退出与未来隔离；条件计算本身由原量价测试覆盖。
 *
 * @author chenxuefeng
 */
class StrategyExecutionTest {
  private final LocalDate start = LocalDate.of(2026, 10, 26);

  private MarketBarDO bar(int offset, double open, double close) {
    var b = new MarketBarDO();
    b.setTradeDate(start.plusDays(offset));
    b.setOpen(BigDecimal.valueOf(open));
    b.setClose(BigDecimal.valueOf(close));
    b.setHigh(BigDecimal.valueOf(Math.max(open, close) + 1));
    b.setLow(BigDecimal.valueOf(Math.min(open, close) - 1));
    b.setFactor(BigDecimal.ONE);
    b.setSynthetic(false);
    b.setSource("TEST");
    b.setAmount(BigDecimal.valueOf(100000000));
    return b;
  }

  private TechnicalReplayInput input(List<MarketBarDO> bars) {
    return input(bars, 3);
  }

  private TechnicalReplayInput input(List<MarketBarDO> bars, int days) {
    return input(bars, days, "600000.SH");
  }

  private TechnicalReplayInput input(List<MarketBarDO> bars, int days, String symbol) {
    var dates =
        java.util.stream.IntStream.range(0, days)
            .mapToObj(start::plusDays)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new TechnicalReplayInput(
        new SignalEvaluationInput(
            SignalStatisticsRequest.builder()
                .symbol(symbol)
                .start(start)
                .end(start.plusDays(days - 1))
                .build(),
            bars,
            List.of(),
            List.of(),
            dates,
            20),
        null,
        dates);
  }

  @Test
  void sharedCashBuysAnEligibleLotAndRejectsALotOverTheStopRiskBudget() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(
              invocation -> {
                PriceVolumeWindow window = invocation.getArgument(0);
                return PriceVolumeStudy.builder()
                    .status("AVAILABLE")
                    .stopReference(window.symbol().equals("600000.SH") ? 32d : 74d)
                    .stopStatus(
                        window.date().equals(start.plusDays(1)) ? "BREACHED" : "ABOVE_REFERENCE")
                    .strategies(
                        List.of(
                            PositionStrategy.builder()
                                .code("OPEN_PULLBACK")
                                .status(window.date().equals(start) ? "CONDITIONAL_MATCH" : "WAIT")
                                .build()))
                    .build();
              });
      var first = input(List.of(bar(0, 35, 35), bar(1, 35, 35.5), bar(2, 35.5, 35.5)), 3);
      var second = input(List.of(bar(0, 80, 80), bar(1, 80, 80), bar(2, 80, 80)), 3, "600001.SH");
      var settings =
          new PortfolioReplaySettings(
              start, start.plusDays(2), 50000, List.of("600000.SH", "600001.SH"));
      var result = PortfolioReplayCalculator.evaluate(settings, List.of(second, first));
      assertEquals(result, PortfolioReplayCalculator.evaluate(settings, List.of(first, second)));
      var period = result.periods().getFirst();
      assertEquals(1, period.tradeStatistics().winningTrades());
      assertEquals(1, period.skipReasons().get("一手超出单股风险预算"));
      assertTrue(period.netReturn() > 0);
      assertFalse(period.openPosition());
    }
  }

  @Test
  void simultaneousBuySignalsCannotSpendTheSameCash() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(
              invocation -> {
                PriceVolumeWindow window = invocation.getArgument(0);
                return PriceVolumeStudy.builder()
                    .stopReference(39.9)
                    .strategies(
                        List.of(
                            PositionStrategy.builder()
                                .code("OPEN_PULLBACK")
                                .status(window.date().equals(start) ? "CONDITIONAL_MATCH" : "WAIT")
                                .build()))
                    .build();
              });
      var symbols = List.of("600000.SH", "600001.SH", "600002.SH", "600003.SH", "600004.SH");
      var bars = List.of(bar(0, 40, 40), bar(1, 40, 40), bar(2, 40, 40));
      var inputs = symbols.stream().map(sym -> input(bars, 3, sym)).toList();
      var settings = new PortfolioReplaySettings(start, start.plusDays(2), 50000, symbols);
      var period = PortfolioReplayCalculator.evaluate(settings, inputs).periods().getFirst();
      assertEquals(1, period.skipReasons().get("可用现金不足一手及费用"));
      assertEquals(20, period.fees(), 1e-9);
      assertEquals(0, period.tradeStatistics().closedTrades());
      assertTrue(period.openPosition());
    }
  }

  @Test
  void buyAndHoldUsesSameOpensAndCostsAndKeepsItsOwnCash() {
    var rows = List.of(bar(-1, 10, 10), bar(0, 10, 10), bar(1, 10, 11), bar(2, 11, 11));
    var settings =
        new PortfolioReplaySettings(start, start.plusDays(2), 50000, List.of("600000.SH"));
    var result = PortfolioReplayCalculator.evaluate(settings, List.of(input(rows)));
    var hold =
        result.comparisons().stream()
            .filter(group -> group.code().equals("BUY_AND_HOLD"))
            .findFirst()
            .orElseThrow()
            .periods()
            .getFirst();
    double cost = 4900 * 10.005;
    assertEquals((4900 * 11 - cost - cost * .0003) / 50000, hold.netReturn(), 1e-9);
    assertEquals(0, hold.completedTrades());
    assertTrue(hold.openPosition());
    assertTrue(hold.reason().contains("1／1"));
  }

  @Test
  void mediumReplayReportsSignalsAndOneLotLimitsForIndependentAccounts() {
    try (var price = mockStatic(PriceVolumeCalculator.class);
        var medium = mockStatic(MediumTermResearchCalculator.class)) {
      price
          .when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(
              invocation -> {
                PriceVolumeWindow window = invocation.getArgument(0);
                return PriceVolumeStudy.builder()
                    .close(window.symbol().equals("600000.SH") ? 10d : 150d)
                    .build();
              });
      medium
          .when(() -> MediumTermResearchCalculator.calculate(any(), any()))
          .thenReturn(Horizon.builder().actions(List.of()).build());
      medium
          .when(() -> MediumTermResearchCalculator.trendConditions(any(), any()))
          .thenAnswer(
              invocation -> {
                PriceVolumeStudy study = invocation.getArgument(1);
                return new MediumTermResearchCalculator.TrendConditions(
                    true, false, false, false, false, study.close() * .99, study.close());
              });
      var first = input(List.of(bar(0, 10, 10), bar(1, 10, 10), bar(2, 10, 10)));
      var second =
          input(List.of(bar(0, 150, 150), bar(1, 150, 150), bar(2, 150, 150)), 3, "600001.SH");
      var settings =
          new PortfolioReplaySettings(
              start, start.plusDays(2), 50000, List.of("600000.SH", "600001.SH"));
      var current = PortfolioReplayCalculator.evaluate(settings, List.of(first, second));
      var reference =
          PortfolioReplayCalculator.evaluate(
              settings, List.of(first, second), PortfolioReplayCalculator.JOURNAL_VERSION);
      assertNull(reference.stockMediumReplays());
      assertEquals(reference.periods(), current.periods());
      var outcome = current.stockMediumReplays();
      assertEquals(1, outcome.getFirst().trend().getFirst().details().entryExecution().filled());
      var expensive = outcome.getLast().trend().getFirst().details();
      assertEquals(3, expensive.entryExecution().signals());
      assertEquals(0, expensive.entryExecution().filled());
      assertEquals(2, expensive.entryExecution().blocked().get("单股仓位上限"));
      assertEquals(1, expensive.entryExecution().blocked().get("区间末尚无下一交易日"));
      var attempt = expensive.orders().getFirst();
      assertEquals(start, attempt.signalDate());
      assertEquals(start.plusDays(1), attempt.executionDate());
      assertEquals(12500d, attempt.entryBudget().positionRoom());
      assertTrue(attempt.entryBudget().oneLotAmount() > attempt.entryBudget().positionRoom());
    }
  }

  @Test
  void volumeConfirmationChangesOnlyNewEntryAndFrozenV2OmitsNewEvidence() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(
              invocation -> {
                PriceVolumeWindow window = invocation.getArgument(0);
                return PriceVolumeStudy.builder()
                    .stopReference(9.8)
                    .stopStatus(
                        window.date().equals(start.plusDays(1)) ? "BREACHED" : "ABOVE_REFERENCE")
                    .volumeRatio5(.95)
                    .strategies(
                        List.of(
                            PositionStrategy.builder()
                                .code("OPEN_PULLBACK")
                                .status(window.date().equals(start) ? "CONDITIONAL_MATCH" : "WAIT")
                                .build()))
                    .build();
              });
      var rows = List.of(bar(0, 10, 10), bar(1, 10, 10), bar(2, 10.1, 10.1));
      var settings =
          new PortfolioReplaySettings(start, start.plusDays(2), 50000, List.of("600000.SH"));
      var prior =
          PortfolioReplayCalculator.evaluate(
              settings, List.of(input(rows)), PortfolioReplayCalculator.PREVIOUS_VERSION);
      var current = PortfolioReplayCalculator.evaluate(settings, List.of(input(rows)));
      var original = current.periods().getFirst();
      assertEquals(prior.periods().getFirst().netReturn(), original.netReturn());
      assertNull(prior.periods().getFirst().details());
      assertEquals(prior.comparisons().size() + 2, current.comparisons().size());
      assertEquals(start.plusDays(1), original.details().closedCycles().getFirst().openedOn());
      assertEquals(start.plusDays(2), original.details().closedCycles().getFirst().closedOn());
      assertEquals(
          original.netReturn() * 50000,
          original.details().closedCycles().getFirst().netPnl(),
          1e-8);
      assertEquals("回踩企稳建仓", original.details().orders().getFirst().trigger());
      assertEquals("收盘触及当日结构止损", original.details().orders().getLast().trigger());
      var filtered =
          current.comparisons().stream()
              .filter(group -> group.code().equals("ORIGINAL_VOLUME"))
              .findFirst()
              .orElseThrow()
              .periods()
              .getFirst();
      assertEquals(1, filtered.details().filteredEntries());
      assertEquals("5日成交额量比低于1.00", filtered.details().orders().getFirst().blockedReason());
      assertEquals(0, filtered.tradeStatistics().closedTrades());
      assertEquals(0, filtered.netReturn());
    }
  }

  @Test
  void openingUsesPastLiquidityAndBlockedExitWaitsForNextOpening() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var finalDay = bar(3, 10, 10);
      var rows = List.of(bar(0, 10, 10), bar(1, 10.1, 9.75), bar(2, 10.3, 10), finalDay);
      var settings =
          new PortfolioReplaySettings(start, start.plusDays(3), 50000, List.of("600000.SH"));
      var initial = PortfolioReplayCalculator.evaluate(settings, List.of(input(rows, 4)));
      var period = initial.periods().getFirst();
      assertEquals(1, period.tradeStatistics().closedTrades());
      assertEquals(1, period.tradeQuality().delayedExitSessions());
      assertEquals(1, period.tradeQuality().longestExitDelay());
      assertFalse(period.tradeQuality().pendingExit());

      // These fields become known only after the final opening. They cannot affect its fill.
      finalDay.setAmount(BigDecimal.ONE);
      finalDay.setHigh(finalDay.getOpen());
      finalDay.setLow(finalDay.getOpen());
      assertEquals(initial, PortfolioReplayCalculator.evaluate(settings, List.of(input(rows, 4))));

      var archived =
          PortfolioReplayCalculator.evaluate(
              settings, List.of(input(rows, 4)), PortfolioReplayCalculator.LEGACY_VERSION);
      assertTrue(archived.periods().getFirst().openPosition());
      assertNull(archived.comparisons());
    }
  }

  private PriceVolumeStudy study(PriceVolumeWindow window) {
    return PriceVolumeStudy.builder()
        .status("AVAILABLE")
        .stopReference(9.8)
        .strategies(
            List.of(
                PositionStrategy.builder()
                    .code("OPEN_PULLBACK")
                    .status(window.date().equals(start) ? "CONDITIONAL_MATCH" : "WAIT")
                    .build()))
        .build();
  }

  @Test
  void entryWaitsUntilNextOpenAndExitPaysCostsAndSlippage() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var rows = List.of(bar(0, 10, 10), bar(1, 10.1, 9.4), bar(2, 9.5, 9.5));
      var result = StrategyReplayCalculator.evaluate(input(rows)).periods().getFirst();
      assertEquals("ASSUMPTION_BASED_REPLAY", result.status());
      assertEquals(1, result.completedTrades());
      assertFalse(result.openPosition());
      assertEquals(1, result.tradeStatistics().closedTrades());
      assertEquals(1, result.tradeStatistics().losingTrades());
      assertEquals(0.0, result.tradeStatistics().winRate());
      assertTrue(result.fees() > 0);
      assertTrue(result.netReturn() < 0);
      double buy = 10.1 * 1.0005, sell = 9.5 * .9995;
      double qty = Math.floor(25000 / (buy * 1.0003) / 100) * 100;
      double fees =
          Math.max(5, qty * buy * .0003) + Math.max(5, qty * sell * .0003) + qty * sell * .0005;
      assertEquals((qty * (sell - buy) - fees) / 100000, result.netReturn(), 1e-10);
    }
  }

  @Test
  void confirmedReductionExecutesNextOpenAndPartialSalesCountAsOneClosedTrade() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(
              invocation -> {
                PriceVolumeWindow window = invocation.getArgument(0);
                long day = java.time.temporal.ChronoUnit.DAYS.between(start, window.date());
                return PriceVolumeStudy.builder()
                    .status("AVAILABLE")
                    .stopReference(9.8)
                    .stopStatus(day == 3 ? "BREACHED" : "ABOVE_REFERENCE")
                    .strategies(
                        List.of(
                            PositionStrategy.builder()
                                .code("OPEN_PULLBACK")
                                .status(day == 0 ? "CONDITIONAL_MATCH" : "WAIT")
                                .build(),
                            PositionStrategy.builder()
                                .code("REDUCE")
                                .status(
                                    day == 1 ? "CONDITIONAL_MATCH" : day == 2 ? "WATCH" : "WAIT")
                                .build()))
                    .build();
              });
      var rows =
          List.of(
              bar(0, 10, 10),
              bar(1, 10, 10),
              bar(2, 10.3, 10.3),
              bar(3, 10.2, 10.2),
              bar(4, 10.1, 10.1));
      var partial = StrategyReplayCalculator.evaluate(input(rows, 3)).periods().getFirst();
      assertEquals(1, partial.completedTrades());
      assertTrue(partial.openPosition());
      assertEquals(0, partial.tradeStatistics().closedTrades());
      assertNull(partial.tradeStatistics().winRate());

      var input = input(rows, 5);
      var result = StrategyReplayCalculator.evaluate(input).periods().getFirst();
      assertEquals(2, result.completedTrades(), "减仓和清仓为两次卖出，观察状态不额外卖出");
      assertFalse(result.openPosition());
      assertEquals(
          new com.alphascope.backtest.vo.StrategySimulation.TradeStatistics(1, 1, 0, 0, 1.0),
          result.tradeStatistics());
      var legacy =
          StrategyReplayCalculator.evaluate(input, StrategyReplayCalculator.LEGACY_VERSION)
              .periods()
              .getFirst();
      assertEquals(1, legacy.completedTrades());
      assertTrue(legacy.openPosition());
      assertNull(legacy.tradeStatistics());
    }
  }

  @Test
  void limitProxySkipsOrdersRatherThanAssumingExecution() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var result =
          StrategyReplayCalculator.evaluate(
                  input(List.of(bar(0, 10, 10), bar(1, 11, 11), bar(2, 11, 11))))
              .periods()
              .getFirst();
      assertEquals(1, result.skippedOrders());
      assertEquals(0, result.completedTrades());
      assertNull(result.tradeStatistics().winRate());
      assertEquals(0.0, result.netReturn());
    }
  }

  @Test
  void futureRowsNeverEnterFrozenPeriodAndMissingExpectedDayIsNotSkipped() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var rows = new ArrayList<>(List.of(bar(0, 10, 10), bar(1, 10.1, 9.4), bar(2, 9.5, 9.5)));
      var original = StrategyReplayCalculator.evaluate(input(rows));
      rows.add(bar(3, 1000, 1000));
      assertEquals(original, StrategyReplayCalculator.evaluate(input(rows)));
      rows.remove(1);
      assertEquals(
          "INCOMPLETE",
          StrategyReplayCalculator.evaluate(input(rows)).periods().getFirst().status());
    }
  }

  @Test
  void heldCorporateActionCannotCreateFictitiousProfit() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var last = bar(2, 5, 5);
      last.setFactor(BigDecimal.valueOf(2));
      var result =
          StrategyReplayCalculator.evaluate(
                  input(List.of(bar(0, 10, 10), bar(1, 10.1, 10.1), last)))
              .periods()
              .getFirst();
      assertEquals("INCOMPLETE", result.status());
      assertNull(result.netReturn());
      assertNull(result.tradeStatistics());
      assertTrue(result.reason().contains("公司行动"));
    }
  }

  @Test
  void explicitSplitLedgerMaintainsNetAssetValueAndLegacyStillRejects() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var last = bar(2, 5.05, 5.05);
      last.setFactor(BigDecimal.valueOf(2));
      var original = input(List.of(bar(0, 10, 10), bar(1, 10.1, 10.1), last));
      var action =
          new com.alphascope.fundamental.dto.CorporateActionInput(
              "600000.SH",
              start.plusDays(2),
              start.plusDays(2),
              start.plusDays(2),
              "TEST",
              BigDecimal.ONE,
              BigDecimal.valueOf(2),
              BigDecimal.valueOf(2),
              BigDecimal.ZERO,
              BigDecimal.valueOf(5.05),
              "https://example.test/announcement");
      var updated =
          new TechnicalReplayInput(
              original.source(),
              original.definition(),
              original.stockTradingDates(),
              List.of(action));
      assertEquals(
          action,
          com.alphascope.common.util.Jsons.read(
              com.alphascope.common.util.Jsons.write(action),
              com.alphascope.fundamental.dto.CorporateActionInput.class));
      var result = StrategyReplayCalculator.evaluate(updated);
      assertEquals(StrategyReplayCalculator.VERSION, result.version());
      assertEquals("ASSUMPTION_BASED_REPLAY", result.periods().getFirst().status());
      assertTrue(Math.abs(result.periods().getFirst().netReturn()) < .001, "送转不能制造翻倍收益");
      assertEquals(
          "INCOMPLETE", StrategyReplayCalculator.evaluate(original).periods().getFirst().status());
    }
  }

  @Test
  void wrongFactorAndSourceCannotAuthorizeCorporateActionAccounting() {
    try (var mock = mockStatic(PriceVolumeCalculator.class)) {
      mock.when(() -> PriceVolumeCalculator.calculate(any()))
          .thenAnswer(inv -> study(inv.getArgument(0)));
      var last = bar(2, 5, 5);
      last.setFactor(BigDecimal.valueOf(2));
      var original = input(List.of(bar(0, 10, 10), bar(1, 10.1, 10.1), last));
      var action =
          new com.alphascope.fundamental.dto.CorporateActionInput(
              "600000.SH",
              start.plusDays(2),
              start.plusDays(2),
              start.plusDays(2),
              "OTHER",
              BigDecimal.ONE,
              BigDecimal.valueOf(2),
              BigDecimal.valueOf(2),
              BigDecimal.ZERO,
              BigDecimal.valueOf(5),
              "https://example.test/announcement");
      var updated =
          new TechnicalReplayInput(
              original.source(),
              original.definition(),
              original.stockTradingDates(),
              List.of(action));
      assertEquals(
          "INCOMPLETE", StrategyReplayCalculator.evaluate(updated).periods().getFirst().status());
    }
  }
}
