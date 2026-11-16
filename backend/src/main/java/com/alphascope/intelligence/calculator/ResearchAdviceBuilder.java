package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.vo.ReportAnalysis;
import com.alphascope.intelligence.vo.ResearchAdvice;
import com.alphascope.intelligence.vo.ThemeResearch;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用已冻结事实解释判断、待验证原因与改变判断的条件，不能把假设写成既成原因。
 *
 * @author chenxuefeng
 */
public final class ResearchAdviceBuilder {
  private ResearchAdviceBuilder() {}

  private static String pct(Double v) {
    return ReportAnalysisBuilder.pct(v);
  }

  private static String fmt(Double v) {
    return ReportAnalysisBuilder.fmt(v);
  }

  private static Double parameter(JsonNode params, String key) {
    var value = params.path(key);
    return value.isNumber() && Double.isFinite(value.doubleValue()) ? value.doubleValue() : null;
  }

  public static List<ResearchAdvice> build(
      List<ReportAnalysis.Stock> stocks,
      List<ReportAnalysis.Assessment> rules,
      List<ReportAnalysis.FinancialFact> financials,
      List<ThemeResearch> themes,
      JsonNode parameters,
      boolean demo) {
    List<ResearchAdvice> out = new ArrayList<>();
    for (var stock : stocks) {
      if (demo || !stock.status().equals("TRADING")) {
        out.add(
            new ResearchAdvice(
                stock.symbol(),
                stock.name(),
                "EVIDENCE_FIRST",
                "先确认业务日真实行情，暂不形成研究倾向。",
                List.of(),
                List.of(),
                List.of(demo ? "切换真实数据后重新生成报告。" : "核对停牌或行情缺口。"),
                List.of()));
        continue;
      }
      var triggered =
          rules.stream()
              .filter(r -> r.symbol().equals(stock.symbol()) && r.status().equals("TRIGGERED"))
              .toList();
      boolean trend =
          triggered.stream()
              .anyMatch(r -> Set.of("TREND_BREAK_20", "TREND_BREAK_60").contains(r.ruleCode()));
      boolean financialRisk =
          triggered.stream()
              .anyMatch(
                  r ->
                      Set.of("PROFIT_SLOWDOWN", "MARGIN_DECLINE", "CASHFLOW_WARNING")
                          .contains(r.ruleCode()));
      List<String> facts = new ArrayList<>(),
          hypotheses = new ArrayList<>(),
          checks = new ArrayList<>(),
          reconsider = new ArrayList<>();
      if (stock.distanceMa20() != null) {
        facts.add(
            "收盘位于20日均线"
                + (stock.distanceMa20() < 0 ? "下方" : "上方")
                + "，偏离"
                + pct(stock.distanceMa20())
                + "；20日相对沪深300为"
                + pct(stock.rs20())
                + "。");
      }
      if (trend) {
        checks.add("后续逐日重算MA20与MA60，记录收盘是否连续两日回到MA20上方、MA20是否停止下降，以及60日趋势规则是否仍触发。");
        reconsider.add("连续两日收盘不低于动态MA20且MA20不再下降，可减少短期趋势转弱的证据；仍须单独复核MA60与基本面。");
      } else {
        checks.add("继续核对价格相对动态MA20/MA60的位置及斜率；规则未触发只表示未满足本模型条件。");
      }
      if (stock.volumeRatio() != null) {
        facts.add("当日复权收益" + pct(stock.dayReturn()) + "、量比" + fmt(stock.volumeRatio()) + "倍。");
        Double volume = parameter(parameters, "sellVolume"),
            sell = parameter(parameters, "sellReturn");
        checks.add(
            volume == null || sell == null
                ? "观察后续上涨日与下跌日的成交量变化，配合趋势与同业验证。"
                : "按冻结参数复核放量下跌：当日收益低于"
                    + pct(sell)
                    + "且量比高于"
                    + fmt(volume)
                    + "倍才触发；后续同时记录上涨日是否有成交量支持，单日缩量不构成趋势修复。");
      }
      var finance = financials.stream().filter(f -> f.symbol().equals(stock.symbol())).findFirst();
      finance.ifPresent(
          f ->
              facts.add(
                  "最新可用累计财报期为"
                      + f.periodEnd()
                      + "，营收同比"
                      + pct(f.revenueYoy())
                      + "、归母利润同比"
                      + pct(f.profitYoy())
                      + "，经营现金流"
                      + fmt(f.operatingCashflow())
                      + "元。"));
      for (var rule : triggered) {
        if (rule.ruleCode().equals("PROFIT_SLOWDOWN")) {
          hypotheses.add("营收扩张未同步转化为利润增长；产品组合、费用投入或成本变化是待查原因，当前证据尚未确认具体因素。");
          checks.add("对照本期与上年同期利润表，分别核对营业成本、销售/管理/研发费用和财务费用的收入占比；下一份财报观察利润同比是否恢复及营收利润增速差是否收窄。");
          reconsider.add("利润增速回升到冻结规则的判断范围之外、且毛利率和现金流证据同步改善时，重新评估利润兑现疑点。");
        }
        if (rule.ruleCode().equals("MARGIN_DECLINE")) {
          Double current = null, previous = null;
          for (var metric : rule.evidence()) {
            if (metric.label().equals("毛利率")) {
              current = metric.value();
            }
            if (metric.label().equals("上年同期毛利率")) {
              previous = metric.value();
            }
          }
          Double threshold = parameter(parameters, "marginDrop");
          if (current != null && previous != null) {
            facts.add(
                "同期毛利率下降"
                    + String.format(Locale.ROOT, "%.4f", (previous - current) * 100)
                    + "个百分点"
                    + (threshold == null
                        ? "。"
                        : "，本模型触发线为"
                            + String.format(Locale.ROOT, "%.4f", threshold * 100)
                            + "个百分点。"));
          }
          hypotheses.add("毛利率同比下降需拆分产品结构、售价、材料成本、产能利用率与汇率等因素；这些是核查方向，不是已确认的下跌原因。");
          checks.add("对照同期财报的分产品/分地区收入与成本、毛利率附注，核对高附加值业务占比和成本传导；保持相同累计期间比较。");
          reconsider.add("下一次同口径披露中毛利率同比降幅回到冻结阈值以内，且收入、利润及现金流可相互印证时，复核盈利质量判断。");
        }
        if (rule.ruleCode().equals("CASHFLOW_WARNING")) {
          checks.add("拆分应收账款、存货与经营性应付项目变化，确认经营现金流连续恶化来自营运资金占用还是持续经营压力。");
          reconsider.add("后续同口径披露不再满足现金流为负且连续恶化的条件时，重新评估现金流风险。");
        }
      }
      for (var theme : themes) {
        if (theme.symbol().equals(stock.symbol())) {
          facts.add(theme.observation());
          hypotheses.addAll(theme.hypotheses());
          checks.addAll(theme.checks());
          if (theme.window20().excessReturn() != null && theme.window20().excessReturn() < -.05) {
            reconsider.add("个股相对同业的20日收益差持续收窄，且趋势与经营证据同步改善时，复核个股偏弱判断；相关性回升本身不是入场信号。");
          }
        }
      }
      String priority = trend || financialRisk ? "REVIEW_FIRST" : "MONITOR";
      String conclusion =
          trend && financialRisk
              ? "趋势与盈利质量均有待核查，当前研究重点是验证经营兑现和相对同业表现，而非仅凭下跌幅度判断反转。"
              : trend
                  ? "趋势证据偏弱，先观察动态均线和相对同业是否修复，再结合经营数据更新判断。"
                  : financialRisk
                      ? "价格趋势与经营证据应分开评估，优先核查已触发的利润、毛利率或现金流问题。"
                      : "本模型未观察到趋势或财报规则触发，继续跟踪同业联动与后续披露，不能据此认定整体低风险。";
      out.add(
          new ResearchAdvice(
              stock.symbol(),
              stock.name(),
              priority,
              conclusion,
              facts,
              hypotheses,
              checks,
              reconsider));
    }
    return out;
  }
}
