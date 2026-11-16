package com.alphascope.signal;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.signal.rule.RiskRules;
import java.util.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 风险规则触发、未触发、证据不足及不适用状态的验证。
 *
 * @author chenxuefeng
 */
class RiskRulesTest {
  @org.junit.jupiter.api.Test
  void watchlistModeSkipsPersonalHoldingsWithoutTreatingThemAsMissing() {
    var rules = new RiskRules(-.03, 1.5, -.1, .2, .05, .03, .1);
    var result =
        rules.evaluate(Map.of("positionWeight", .99), false, false).stream()
            .filter(r -> r.ruleCode().equals("POSITION_CONCENTRATION"))
            .findFirst()
            .orElseThrow();
    assertEquals("NOT_APPLICABLE", result.status());
    assertTrue(result.evidence().isEmpty());
  }

  private final RiskRules rules = new RiskRules(-.03, 1.5, -.1, .2, .05, .03, .1);

  @Test
  void missingEvidenceNeverTriggers() {
    assertTrue(
        rules.evaluate(Map.of(), false).stream()
            .allMatch(r -> r.status().equals("INSUFFICIENT_DATA")));
  }

  @Test
  void strictVolumeBoundary() {
    var e = Map.of("return", -.03, "volumeRatio", 1.5);
    assertEquals(
        "NOT_TRIGGERED",
        rules.evaluate(e, false).stream()
            .filter(r -> r.ruleCode().equals("VOLUME_SELL"))
            .findFirst()
            .orElseThrow()
            .status());
  }

  @Test
  void sellRequiresBothConditions() {
    var e = Map.of("return", -.04, "volumeRatio", 1.6);
    assertEquals(
        "TRIGGERED",
        rules.evaluate(e, false).stream()
            .filter(r -> r.ruleCode().equals("VOLUME_SELL"))
            .findFirst()
            .orElseThrow()
            .status());
  }

  @Test
  void syntheticEvidenceCannotTrigger() {
    var e = Map.of("return", -.9, "volumeRatio", 20.0);
    assertTrue(
        rules.evaluate(e, true).stream().allMatch(r -> r.status().equals("INSUFFICIENT_DATA")));
  }

  @Test
  void cashflowRequiresComparableThreePeriods() {
    var e = Map.of("cashflow", -3.0, "previousYearCashflow", -2.0, "twoYearsAgoCashflow", -1.0);
    assertEquals(
        "TRIGGERED",
        rules.evaluate(e, false).stream()
            .filter(r -> r.ruleCode().equals("CASHFLOW_WARNING"))
            .findFirst()
            .orElseThrow()
            .status());
  }
}
