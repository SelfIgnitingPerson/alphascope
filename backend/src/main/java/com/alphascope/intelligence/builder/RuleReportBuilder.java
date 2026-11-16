package com.alphascope.intelligence.builder;

import com.alphascope.common.util.Jsons;
import com.alphascope.intelligence.calculator.ReportAnalysisBuilder;
import com.alphascope.intelligence.dto.ReportResult;
import com.alphascope.intelligence.dto.StockEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将同一份冻结证据转成正文，数据缺项不当作已经发生的不利市场事实。
 *
 * @author chenxuefeng
 */
public final class RuleReportBuilder {
  public static ReportResult build(
      String type, LocalDate date, List<StockEvidence> stocks, Set<String> refs, boolean demo) {
    try {
      var input = new ObjectMapper().readTree(Jsons.write(Map.of("watchlist", stocks)));
      return build(type, date, input, refs, demo);
    } catch (IOException e) {
      throw new IllegalStateException("无法读取报告事实", e);
    }
  }

  public static ReportResult build(
      String type, LocalDate date, JsonNode input, Set<String> refs, boolean demo) {
    var analysis = ReportAnalysisBuilder.build(input, type, date, demo);
    List<String> facts = new ArrayList<>(),
        positive = new ArrayList<>(),
        negative = new ArrayList<>(),
        risks = new ArrayList<>(),
        checks = new ArrayList<>();
    for (var stock : analysis.stocks()) {
      String name = stock.name() + "（" + stock.symbol() + "）";
      if (!stock.status().equals("TRADING")) {
        facts.add(name + "：" + (stock.status().equals("SUSPENDED") ? "本期停牌" : "缺少业务日有效行情") + "。");
        continue;
      }
      facts.add(
          name
              + "收盘"
              + ReportAnalysisBuilder.fmt(stock.close())
              + "元，"
              + (type.equals("WEEKLY") ? "本周" : "当日")
              + "复权收益"
              + ReportAnalysisBuilder.pct(stock.periodReturn())
              + "，量比"
              + ReportAnalysisBuilder.fmt(stock.volumeRatio())
              + "倍。");
      String trend =
          name
              + stock.trend()
              + "；距20日均线"
              + ReportAnalysisBuilder.pct(stock.distanceMa20())
              + "，20日相对沪深300"
              + ReportAnalysisBuilder.pct(stock.rs20())
              + "。";
      if (stock.distanceMa20() != null) {
        if (stock.distanceMa20() >= 0) {
          positive.add(trend);
        } else {
          negative.add(trend);
        }
      }
    }
    for (var rule : analysis.assessments()) {
      if (rule.status().equals("TRIGGERED")) {
        List<String> metrics =
            rule.evidence().stream()
                .map(
                    m ->
                        m.label()
                            + "="
                            + (m.unit().equals("PERCENT")
                                ? ReportAnalysisBuilder.pct(m.value())
                                : ReportAnalysisBuilder.fmt(m.value())
                                    + (m.unit().equals("TIMES") ? "倍" : "元")))
                .toList();
        risks.add(
            rule.name()
                + " · "
                + rule.date()
                + " · "
                + rule.ruleName()
                + "："
                + String.join("，", metrics)
                + "。");
      }
    }
    for (var f : analysis.financials()) {
      facts.add(
          f.name()
              + "最新可用累计财报期为"
              + f.periodEnd()
              + "，营收同比"
              + ReportAnalysisBuilder.pct(f.revenueYoy())
              + "，归母利润同比"
              + ReportAnalysisBuilder.pct(f.profitYoy())
              + "，毛利率"
              + ReportAnalysisBuilder.pct(f.grossMargin())
              + "，经营现金流"
              + ReportAnalysisBuilder.fmt(f.operatingCashflow())
              + "元。");
    }
    if (!analysis.advice().isEmpty()) {
      for (var advice : analysis.advice()) {
        facts.add(advice.name() + "：" + advice.conclusion());
        for (var check : advice.verifyNext()) {
          checks.add(advice.symbol() + " · " + check);
        }
      }
    } else {
      for (var check : analysis.checks()) {
        checks.add(check.symbol() + " · " + check.title() + "：" + check.detail());
      }
    }
    if (analysis.priceVolume() != null) {
      for (var study : analysis.priceVolume()) {
        if (!"AVAILABLE".equals(study.status())) {
          checks.add(study.name() + "：两月量价研究缺项：" + String.join("；", study.missing()));
          continue;
        }
        facts.add(study.name() + "近两月量价：" + study.conclusion());
        checks.add(
            study.name()
                + "止损参考："
                + (study.stopReference() == null ? "暂无有效值" : study.stopReference() + "元")
                + ("BREACHED".equals(study.stopStatus()) ? "（已触及或跌破）" : "")
                + "；压力区间："
                + (study.resistances().isEmpty()
                    ? "此前两月样本上方无可核验区间"
                    : study.resistances().stream()
                        .map(zone -> zone.lower() + "—" + zone.upper() + "元")
                        .toList()));
        if (study.strategies() != null) {
          for (var strategy : study.strategies()) {
            if (!strategy.code().equals("REDUCE")
                && strategy.status().equals("CONDITIONAL_MATCH")) {
              checks.add(study.name() + " · " + strategy.title() + "：" + strategy.conclusion());
            }
          }
        }
        if (Set.of("REDUCE_REVIEW", "PROFIT_PROTECTION_REVIEW").contains(study.decision())) {
          risks.add(study.name() + "：" + study.conclusion());
        }
      }
    }
    var themeOutlook = new ArrayList<String>();
    for (var theme : analysis.themes()) {
      themeOutlook.add(
          theme.symbol() + " · " + theme.title() + "：" + theme.observation() + " 仅限披露样本，行业覆盖率未知。");
    }
    return new ReportResult(
        "UNKNOWN",
        analysis.headline() + " " + String.join(" ", facts),
        positive,
        negative,
        themeOutlook,
        risks,
        checks,
        new ArrayList<>(refs),
        analysis.methodNotes());
  }
}
