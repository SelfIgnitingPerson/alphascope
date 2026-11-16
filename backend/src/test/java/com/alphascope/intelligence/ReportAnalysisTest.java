package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.alphascope.intelligence.builder.RuleReportBuilder;
import com.alphascope.intelligence.calculator.ReportAnalysisBuilder;
import com.alphascope.intelligence.dto.StockEvidence;
import com.alphascope.market.dto.MarketBarDO;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 冻结报告分析、时点口径及缺失证据边界的回归验证。
 *
 * @author chenxuefeng
 */
class ReportAnalysisTest {
  @Test
  void storedHistoryProvenanceKeepsTheOriginalCollectionTime() {
    var root = input(json.createArrayNode().add(row(date, 50, 1)));
    root.putArray("marketAcquisition")
        .addObject()
        .put("symbol", "300476.SZ")
        .put("mode", "STORED_HISTORY")
        .put("source", "BAOSTOCK")
        .put("validatedFrom", "2024-10-30")
        .put("validatedThrough", date.toString())
        .put("rows", 488)
        .put("oldestFetchedAt", "2026-10-31T17:18:00")
        .put("newestFetchedAt", "2026-10-31T21:32:00")
        .put("note", "未联网复核供应商最新修订");
    var before = root.deepCopy();
    var a = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    assertEquals("STORED_HISTORY", a.marketAcquisition().getFirst().mode());
    assertEquals(
        LocalDateTime.of(2026, 10, 31, 21, 32), a.marketAcquisition().getFirst().newestFetchedAt());
    assertEquals(before, root);
  }

  @Test
  void adviceUsesFrozenRuleThresholdsAndSeparatesFactsFromPossibleCauses() {
    var root =
        input(json.createArrayNode().add(row(date.minusDays(1), 60, 1)).add(row(date, 50, 1)));
    root.putObject("ruleParameters")
        .put("sellReturn", -.07)
        .put("sellVolume", 3.5)
        .put("marginDrop", .03);
    signals(root)
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "PROFIT_SLOWDOWN")
        .put("status", "TRIGGERED");
    var a = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    var advice = a.advice().getFirst();
    assertEquals("REVIEW_FIRST", advice.priority());
    assertTrue(
        advice.verifyNext().stream().anyMatch(s -> s.contains("-7.00%") && s.contains("3.50")));
    assertTrue(advice.hypotheses().stream().anyMatch(s -> s.contains("尚未确认")));
    assertFalse(advice.reconsiderIf().isEmpty());
    root.remove("ruleParameters");
    assertTrue(
        ReportAnalysisBuilder.build(root, "DAILY", date, false).advice().isEmpty(),
        "Old snapshots must not inherit current thresholds");
  }

  @Test
  void frozenThemeReasonIsShownWithoutReconstructingHistoricalMembers() {
    var root = input(json.createArrayNode().add(row(date, 50, 1)));
    String reason = "PCB：当前登记1只成员；最近3个交易日仅保存1/3天记录，缺少2026-10-28、2026-10-29。";
    var evidence = json.createObjectNode();
    evidence.putObject("values");
    evidence.put("reason", reason);
    signals(root)
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "INDUSTRY_DIVERGENCE")
        .put("status", "INSUFFICIENT_DATA")
        .set("evidence_json", evidence);
    var analysis = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    assertEquals(reason, analysis.assessments().getFirst().explanation());
    assertEquals(4, analysis.assessments().getFirst().missingInputs().size());
  }

  @Test
  void notApplicablePositionIsExcludedFromMissingCountAndMarketScopeIsPreserved() {
    var root = input(json.createArrayNode().add(row(date, 50, 1)));
    root.put("researchMode", "WATCHLIST");
    signals(root)
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "POSITION_CONCENTRATION")
        .put("status", "NOT_APPLICABLE");
    root.putObject("market")
        .putObject("snapshot")
        .put("status", "COMPLETE")
        .put("universeScope", "SH_SZ_A")
        .put("amount", 100000000)
        .put("advancers", 2000)
        .put("decliners", 3000)
        .put("coverage", 1);
    var a = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    assertEquals(0, a.stocks().getFirst().insufficient());
    assertEquals("NOT_APPLICABLE", a.assessments().getFirst().status());
    assertEquals("SH_SZ_A", a.market().scope());
    assertTrue(a.missingData().stream().anyMatch(s -> s.contains("已接入沪深A股")));
    assertFalse(a.missingData().stream().anyMatch(s -> s.contains("全A成交额、全A广度和盈利预期未接入")));
  }

  @Test
  void financialFactsRejectNotYetAvailableRevisions() {
    var root = input(json.createArrayNode().add(row(date, 50, 1)));
    var f = ((ArrayNode) root.path("watchlist").get(0).path("financials")).addObject();
    f.put("period_end", "2026-09-30")
        .put("report_type", "Q3_CUMULATIVE")
        .put("published_at", "2026-10-30T00:00:00")
        .put("available_at", "2026-10-31T00:00:00");
    assertTrue(ReportAnalysisBuilder.build(root, "DAILY", date, false).financials().isEmpty());
    f.put("available_at", "2026-10-30T09:00:00");
    assertEquals(1, ReportAnalysisBuilder.build(root, "DAILY", date, false).financials().size());
  }

  final ObjectMapper json = new ObjectMapper();
  final LocalDate date = LocalDate.of(2026, 10, 30);

  ObjectNode row(LocalDate day, double close, double factor) {
    var row = json.createObjectNode();
    row.put("trade_date", day.toString());
    row.put("close", close);
    row.put("factor", factor);
    row.put("source", "BAOSTOCK");
    row.put("batch_id", "frozen-batch");
    return row;
  }

  ArrayNode signals(ObjectNode root) {
    return (ArrayNode) root.path("watchlist").get(0).path("signals");
  }

  ObjectNode input(ArrayNode bars) {
    var root = json.createObjectNode();
    var stock = root.putArray("watchlist").addObject();
    stock.put("symbol", "300476.SZ");
    stock.put("name", "测试标的");
    stock.set("history", bars);
    stock.putArray("financials");
    stock.putArray("signals");
    var ind = stock.putArray("indicators").addObject();
    ind.put("trade_date", date.toString());
    ind.put("ma20", 50);
    ind.put("ma60", 60);
    ind.put("rs20", -.1);
    ind.put("vol_ratio", 2);
    return root;
  }

  @Test
  void oldSnapshotWithoutPricesCannotBeReconstructed() {
    var root = json.createObjectNode();
    root.putArray("watchlist").addObject().put("symbol", "300476.SZ");
    var analysis = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    assertEquals("LEGACY_NO_MARKET_SNAPSHOT", analysis.status());
    assertTrue(analysis.stocks().isEmpty());
  }

  @Test
  void stockSplitDoesNotBecomeFalseLossAndLevelsShareAnchor() {
    var bars = json.createArrayNode();
    for (int n = 20; n >= 1; n--) bars.add(row(date.minusDays(n), 100, 1));
    bars.add(row(date, 50, 2));
    var root = input(bars);
    var before = root.deepCopy();
    var analysis = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    var s = analysis.stocks().getFirst();
    assertEquals(0, s.dayReturn(), 1e-9);
    assertEquals(0, s.return20(), 1e-9);
    assertEquals(50, s.previous20Low(), 1e-9);
    assertEquals(0, s.distanceMa20(), 1e-9);
    assertEquals(before, root);
  }

  @Test
  void futureRowsAreIgnoredAndStaleRulesDoNotBecomeCurrentJudgment() {
    var bars =
        json.createArrayNode()
            .add(row(date.minusDays(2), 100, 1))
            .add(row(date.minusDays(1), 90, 1))
            .add(row(date.plusDays(1), 999, 1));
    var root = input(bars);
    signals(root)
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "TREND_BREAK_60")
        .put("status", "TRIGGERED");
    var a = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    assertEquals(90, a.stocks().getFirst().close());
    assertNull(a.stocks().getFirst().dayReturn());
    assertEquals("PARTIAL", a.status());
    assertEquals("INSUFFICIENT_DATA", a.assessments().getFirst().status());
  }

  @Test
  void weeklyReturnUsesTheLastCloseBeforeMonday() {
    var bars =
        json.createArrayNode()
            .add(row(LocalDate.of(2026, 10, 23), 100, 1))
            .add(row(LocalDate.of(2026, 10, 26), 110, 1))
            .add(row(LocalDate.of(2026, 10, 27), 121, 1));
    var a = ReportAnalysisBuilder.build(input(bars), "WEEKLY", LocalDate.of(2026, 10, 27), false);
    assertEquals(.21, a.stocks().getFirst().periodReturn(), 1e-9);
    assertEquals(.1, a.stocks().getFirst().dayReturn(), 1e-9);
  }

  @Test
  void ruleEvidenceIsReadableAndMissingInputsAreExplicit() throws Exception {
    var root =
        input(json.createArrayNode().add(row(date.minusDays(1), 60, 1)).add(row(date, 50, 1)));
    var signals = signals(root);
    signals
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "VOLUME_SELL")
        .put("status", "TRIGGERED")
        .put("severity", "HIGH")
        .put("evidence_json", "{\"values\":{\"return\":-0.1666666667,\"volumeRatio\":2}}");
    signals
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "PROFIT_SLOWDOWN")
        .put("status", "INSUFFICIENT_DATA");
    var a = ReportAnalysisBuilder.build(root, "DAILY", date, false);
    assertEquals("放量下跌", a.assessments().getFirst().ruleName());
    assertTrue(
        a.assessments().getFirst().evidence().stream()
            .anyMatch(m -> m.label().equals("量比") && m.value() == 2));
    assertEquals(List.of("营收同比", "利润同比"), a.assessments().getLast().missingInputs());
    assertEquals(1, a.stocks().getFirst().triggered());
  }

  @Test
  void demoCannotProduceRealTriggeredAssessments() {
    var root = input(json.createArrayNode().add(row(date, 50, 1)));
    signals(root)
        .addObject()
        .put("trade_date", date.toString())
        .put("rule_code", "TREND_BREAK_60")
        .put("status", "TRIGGERED");
    var a = ReportAnalysisBuilder.build(root, "DAILY", date, true);
    assertEquals("DEMO", a.status());
    assertEquals(0, a.stocks().getFirst().triggered());
  }

  @Test
  void missingMarketCoverageIsNotFabricatedAsANegativePriceFact() {
    var stock =
        new StockEvidence(
            "300476.SZ",
            "测试标的",
            List.of(fixtureMarketBarDO1()),
            List.of(fixtureIndicatorSnapshot2()),
            List.of(),
            List.of(),
            false,
            List.of());
    var result = RuleReportBuilder.build("DAILY", date, List.of(stock), Set.of(), false);
    assertTrue(result.negativeFactors().isEmpty());
    assertTrue(result.themeOutlook().isEmpty());
    assertFalse(result.positiveFactors().isEmpty());
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private MarketBarDO fixtureMarketBarDO1() {
    var row = new MarketBarDO();
    row.setTradeDate(date);
    row.setClose(BigDecimal.valueOf(50));
    row.setFactor(BigDecimal.valueOf(1));
    row.setSource("BAOSTOCK");
    return row;
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private IndicatorSnapshot fixtureIndicatorSnapshot2() {
    var row = new IndicatorSnapshot();
    row.setTradeDate(date);
    row.setMa20(BigDecimal.valueOf(40));
    row.setMa60(BigDecimal.valueOf(45));
    return row;
  }
}
