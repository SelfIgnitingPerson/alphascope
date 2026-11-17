package com.alphascope.backtest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.alphascope.backtest.calculator.StrategyReplayCalculator;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.intelligence.vo.PositionStrategy;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    return new TechnicalReplayInput(
        new SignalEvaluationInput(
            SignalStatisticsRequest.builder()
                .symbol("600000.SH")
                .start(start)
                .end(start.plusDays(2))
                .build(),
            bars,
            List.of(),
            List.of(),
            Set.of(start, start.plusDays(1), start.plusDays(2)),
            20),
        null,
        Set.of(start, start.plusDays(1), start.plusDays(2)));
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
      assertTrue(result.reason().contains("公司行动"));
    }
  }
}
