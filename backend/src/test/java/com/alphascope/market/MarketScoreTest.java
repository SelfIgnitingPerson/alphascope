package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.calculator.MarketScoreCalculator;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 全市场评分维度齐备及缺项保留 UNKNOWN 的验证。
 *
 * @author chenxuefeng
 */
class MarketScoreTest {
  @Test
  void noBreadthMeansUnknown() {
    var result =
        MarketScoreCalculator.calculate(
            Map.of("trend", new MarketScoreCalculator.Dimension(20.0, Map.of())));
    assertNull(result.score());
    assertEquals("UNKNOWN", result.state());
  }

  @Test
  void fiveDimensionsAreSummed() {
    Map<String, MarketScoreCalculator.Dimension> values = new HashMap<>();
    for (String key :
        List.of(
            "trend", "turnoverDirection", "breadth", "themeRelativeStrength", "leaderConfirmation"))
      values.put(key, new MarketScoreCalculator.Dimension(16.0, Map.of()));
    assertEquals("STRONG", MarketScoreCalculator.calculate(values).state());
  }
}
