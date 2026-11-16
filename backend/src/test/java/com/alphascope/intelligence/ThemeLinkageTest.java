package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.intelligence.calculator.ThemeLinkageCalculator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 披露样本联动、收益窗口及覆盖边界的验证。
 *
 * @author chenxuefeng
 */
class ThemeLinkageTest {
  final ObjectMapper json = new ObjectMapper();
  final LocalDate date = LocalDate.of(2026, 10, 30);
  final List<LocalDate> dates = new ArrayList<>();

  ThemeLinkageTest() {
    for (LocalDate d = date.minusDays(115); !d.isAfter(date); d = d.plusDays(1))
      if (d.getDayOfWeek().getValue() < 6) dates.add(d);
  }

  ArrayNode bars(int sign) {
    var out = json.createArrayNode();
    double price = 100;
    for (int i = 0; i < dates.size(); i++) {
      if (i > 0) price *= 1 + sign * (.01 * Math.sin(i * .6) + .002 * Math.cos(i * .13));
      out.addObject()
          .put("trade_date", dates.get(i).toString())
          .put("close", price)
          .put("factor", 1)
          .put("source", "BAOSTOCK")
          .put("is_synthetic", false);
    }
    return out;
  }

  ObjectNode input(int peers) {
    var root = json.createObjectNode();
    root.set("benchmark", bars(1));
    var stock = root.putArray("watchlist").addObject().put("symbol", "300476.SZ");
    stock.set("history", bars(1));
    var theme = root.putArray("themeContexts").addObject().put("code", "PCB");
    var cohort = theme.putObject("cohort");
    cohort
        .put("id", 1)
        .put("scope", "DISCLOSED_RESEARCH_SAMPLE")
        .put("title", "披露样本")
        .put("available_at", "2026-03-22T00:00:00")
        .put("expected_members", peers + 1)
        .put("source_url", "https://example.test/source");
    var expected = theme.putArray("expectedDates");
    dates.forEach(d -> expected.add(d.toString()));
    var members = theme.putArray("members");
    members.addObject().put("symbol", "300476.SZ").set("history", bars(1));
    for (int i = 0; i < peers; i++)
      members.addObject().put("symbol", "PEER" + i).set("history", bars(-1));
    theme.putArray("scores");
    return root;
  }

  @Test
  void commonWeaknessRequiresThreeConsecutiveCoveredDatesAndIsNotCalledStrength() {
    var root = input(2);
    var scores = (ArrayNode) root.path("themeContexts").get(0).path("scores");
    for (var d : dates.subList(dates.size() - 3, dates.size())) {
      var row =
          scores
              .addObject()
              .put("trade_date", d.toString())
              .put("model_version", "EQUAL_RETURN_V1")
              .put("day_return", -.02)
              .put("breadth", 0)
              .put("coverage", 1);
      row.putObject("evidence_json")
          .put("cohortId", 1)
          .put("validMembers", 3)
          .put("totalMembers", 3);
    }
    var result = ThemeLinkageCalculator.calculate(root, date, false).getFirst();
    assertTrue(result.observation().contains("共同偏弱"));
    assertTrue(result.observation().contains("分化规则未触发不等于主题强势"));
    assertTrue(result.observation().contains("60日个股收益"));
    ((ObjectNode) scores.get(0)).put("trade_date", dates.get(dates.size() - 4).toString());
    assertFalse(
        ThemeLinkageCalculator.calculate(root, date, false)
            .getFirst()
            .observation()
            .contains("共同偏弱"));
  }

  @Test
  void pearsonBoundsNegativeRelationAndUndefinedVariance() {
    assertEquals(
        1,
        ThemeLinkageCalculator.correlation(new double[] {1, 2, 3}, new double[] {2, 4, 6}),
        1e-12);
    assertEquals(
        -1,
        ThemeLinkageCalculator.correlation(new double[] {1, 2, 3}, new double[] {6, 4, 2}),
        1e-12);
    assertNull(ThemeLinkageCalculator.correlation(new double[] {1, 1, 1}, new double[] {1, 2, 3}));
    assertNull(
        ThemeLinkageCalculator.correlation(new double[] {Double.NaN, 1}, new double[] {1, 2}));
  }

  @Test
  void controlsSharedMarketFactorWithoutClaimingCausation() {
    var x = new double[60];
    var y = new double[60];
    var m = new double[60];
    double[] market = {-1, -1, 1, 1}, a = {-1, 1, -1, 1}, b = {-1, 1, 1, -1};
    for (int i = 0; i < 60; i++) {
      m[i] = market[i % 4];
      x[i] = 2 * m[i] + .1 * a[i % 4];
      y[i] = 2 * m[i] + .1 * b[i % 4];
    }
    assertTrue(ThemeLinkageCalculator.correlation(x, y) > .99);
    assertEquals(0, ThemeLinkageCalculator.adjustedCorrelation(x, y, m), 1e-10);
  }

  @Test
  void excludesSelfAndUsesExactlyTwentyAndSixtyAlignedReturns() {
    var root = input(2);
    var before = root.deepCopy();
    var result = ThemeLinkageCalculator.calculate(root, date, false).getFirst();
    assertEquals(List.of("PEER0", "PEER1"), result.window20().peers());
    assertEquals(2, result.window20().expectedPeers());
    assertEquals(20, result.window20().samples());
    assertEquals(60, result.window60().samples());
    assertEquals(-1, result.window20().correlation(), 1e-10);
    assertEquals("AVAILABLE", result.status());
    assertEquals(before, root);
  }

  @Test
  void missingTradingDateDoesNotBecomeZeroOrShrinkTheDenominator() {
    var root = input(3);
    ((ArrayNode) root.path("themeContexts").get(0).path("members").get(1).path("history"))
        .remove(dates.size() - 4);
    var result = ThemeLinkageCalculator.calculate(root, date, false).getFirst();
    assertEquals(3, result.window20().expectedPeers());
    assertEquals(2, result.window20().peers().size());
    assertEquals(2d / 3, result.window20().coverage(), 1e-12);
    assertNull(result.window20().correlation());
    assertEquals("INSUFFICIENT_DATA", result.status());
  }

  @Test
  void incompleteFocalHistoryAndTooFewPeersCannotProduceAClaim() {
    var root = input(2);
    ((ArrayNode) root.path("watchlist").get(0).path("history")).remove(dates.size() - 3);
    assertNull(
        ThemeLinkageCalculator.calculate(root, date, false).getFirst().window20().correlation());
    assertEquals(
        "INSUFFICIENT_DATA",
        ThemeLinkageCalculator.calculate(input(1), date, false).getFirst().status());
  }

  @Test
  void noFutureDisclosureOrFutureBarsAreUsed() {
    var root = input(2);
    var before = ThemeLinkageCalculator.calculate(root, date, false).getFirst();
    ((ArrayNode) root.path("watchlist").get(0).path("history"))
        .addObject()
        .put("trade_date", date.plusDays(1).toString())
        .put("close", 999999)
        .put("factor", 9999)
        .put("source", "BAOSTOCK");
    var after = ThemeLinkageCalculator.calculate(root, date, false).getFirst();
    assertEquals(before.window60(), after.window60());
    ((ObjectNode) root.path("themeContexts").get(0).path("cohort"))
        .put("available_at", "2026-10-31T00:00:00");
    assertTrue(ThemeLinkageCalculator.calculate(root, date, false).isEmpty());
  }

  @Test
  void splitAdjustmentPreventsAFabricatedLossAndMixedSourcesAreRejected() {
    var root = input(2);
    var history = (ArrayNode) root.path("watchlist").get(0).path("history");
    for (int i = 0; i < history.size(); i++)
      ((ObjectNode) history.get(i))
          .put("close", i == history.size() - 1 ? 50 : 100)
          .put("factor", i == history.size() - 1 ? 2 : 1);
    var result = ThemeLinkageCalculator.calculate(root, date, false).getFirst();
    assertEquals(0, result.window20().stockReturn(), 1e-12);
    assertNull(result.window20().correlation());
    ((ObjectNode) history.get(history.size() - 2)).put("source", "SINA");
    assertEquals(
        "INSUFFICIENT_DATA",
        ThemeLinkageCalculator.calculate(root, date, false).getFirst().status());
  }

  @Test
  void demoCannotProduceRealThemeResearch() {
    assertTrue(ThemeLinkageCalculator.calculate(input(2), date, true).isEmpty());
  }
}
