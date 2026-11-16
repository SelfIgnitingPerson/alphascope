package com.alphascope.indicator;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.indicator.calculator.IndicatorCalculator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 技术指标窗口、复权输入及历史不足情形的回归验证。
 *
 * @author chenxuefeng
 */
class IndicatorCalculatorTest {
  @Test
  void insufficientHistoryIsNotZero() {
    assertTrue(IndicatorCalculator.ma(List.of(1.0), 20).isEmpty());
  }

  @Test
  void volumeBaselineExcludesCurrent() {
    assertEquals(10.0, IndicatorCalculator.volumeRatio(List.of(2.0, 2.0, 20.0), 2).orElseThrow());
  }

  @Test
  void identicalReturnsHaveZeroRelativeStrength() {
    assertEquals(
        0,
        IndicatorCalculator.rs(List.of(10.0, 12.0), List.of(100.0, 120.0), 1).orElseThrow(),
        1e-10);
  }

  @Test
  void atrUsesWilderInitialization() {
    assertEquals(
        2.0,
        IndicatorCalculator.atr(
                List.of(10.0, 11.0, 12.0), List.of(8.0, 9.0, 10.0), List.of(9.0, 10.0, 11.0), 2)
            .orElseThrow());
  }
}
