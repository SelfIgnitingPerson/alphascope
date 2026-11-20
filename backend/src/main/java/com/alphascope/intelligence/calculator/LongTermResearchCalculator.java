package com.alphascope.intelligence.calculator;

import static com.alphascope.intelligence.calculator.HoldingResearchSupport.ASSUMPTIONS;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.average;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.checkedBars;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.money;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.percent;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.unavailable;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.value;

import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.vo.HoldingResearchView.Action;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 长期财报与经营缺项判断；不把技术均线当估值。
 *
 * @author chenxuefeng
 */
final class LongTermResearchCalculator {
  private LongTermResearchCalculator() {}

  static Horizon calculate(HoldingResearchInput input, PriceVolumeStudy study) {
    if (!"AVAILABLE".equals(study.status()))
      return unavailable("LONG", "当日行情不可用，长期技术复核价暂不输出；请先补齐行情证据");
    var missing = new ArrayList<String>();
    Double ma250 = null;
    try {
      ma250 = money(average(checkedBars(input, 250)));
    } catch (IllegalArgumentException gap) {
      missing.add(gap.getMessage());
    }
    var visibleReports = ValuationAuditCalculator.visible(input.stock().financials(), input.asOf());
    var financial = ValuationAuditCalculator.latest(visibleReports);
    boolean freshFinancial =
        financial != null && !financial.getPeriodEnd().isBefore(input.asOf().minusDays(200));
    if (!freshFinancial) missing.add("最近200天内的有效累计财报期末与公开来源");
    if (financial == null || financial.getNetProfit() == null) missing.add("已披露归母净利润");
    if (financial == null || financial.getOperatingCashflow() == null) missing.add("已披露经营现金流");
    if (financial == null || financial.getRevenueYoy() == null || financial.getProfitYoy() == null)
      missing.add("同口径营收与利润同比");
    var audit = input.valuationAudit();
    boolean audited =
        audit != null
            && input.asOf().equals(audit.date())
            && "CHECKED_WITH_LIMITS".equals(audit.status())
            && audit.conservativeCap() != null;
    if (!audited) missing.add("独立估值核验未完成，需补齐实际股本、同源滚动盈利和有效同行；用户勾选不能解除缺项");
    var thesis = input.valuationThesis();
    boolean knownThesis =
        thesis != null
            && thesis.symbol().equals(input.stock().symbol())
            && !thesis.asOf().isAfter(input.asOf());
    boolean valued = knownThesis && !thesis.validUntil().isBefore(input.asOf());
    var exitChecks =
        LongTermExitConditions.evaluate(
            knownThesis ? thesis.exitPolicy() : null, visibleReports, financial, input.asOf());
    boolean exit = exitChecks.stream().anyMatch(c -> Boolean.TRUE.equals(c.met()));
    Boolean exitClear =
        exitChecks.isEmpty() || exitChecks.stream().anyMatch(c -> c.met() == null) ? null : !exit;
    if (exitChecks.isEmpty()) missing.add("缺少已登记的可量化长期退出条件");
    else if (exitClear == null) missing.add("尚缺连续同源财报，无法完整核对长期退出条件");
    boolean business = valued && thesis.businessConfirmed();
    Double valueLow = valued ? money(thesis.epsLow().multiply(thesis.peLow()).doubleValue()) : null;
    Double valueHigh =
        valued ? money(thesis.epsHigh().multiply(thesis.peHigh()).doubleValue()) : null;
    Double entryLow = valued ? money(valueLow * (1 - thesis.safetyMargin().doubleValue())) : null;

    if (!valued) missing.add("尚无当前用户有效的EPS、PE估值情景与安全边际，或假设已过复核期限");
    if (!business) missing.add("竞争优势、订单与产业逻辑尚未由用户依据公开资料确认");
    if (audited && valued) entryLow = money(Math.min(entryLow, audit.conservativeCap()));
    Boolean earnings =
        financial == null
                || financial.getNetProfit() == null
                || financial.getOperatingCashflow() == null
            ? null
            : financial.getNetProfit().signum() > 0
                && financial.getOperatingCashflow().signum() > 0;
    Boolean growth =
        financial == null || financial.getRevenueYoy() == null || financial.getProfitYoy() == null
            ? null
            : financial.getRevenueYoy().signum() > 0 && financial.getProfitYoy().signum() > 0;
    Boolean financialRisk =
        !freshFinancial
                || financial == null
                || financial.getNetProfit() == null
                || financial.getOperatingCashflow() == null
            ? null
            : financial.getNetProfit().signum() <= 0
                || financial.getOperatingCashflow().signum() < 0;
    var financialEvents =
        input.stock().signals().stream()
            .filter(
                r ->
                    input.asOf().equals(r.getTradeDate())
                        && Set.of("PROFIT_SLOWDOWN", "CASHFLOW_WARNING", "MARGIN_DECLINE")
                            .contains(r.getRuleCode()))
            .toList();
    var currentRules =
        financialEvents.stream().filter(r -> "TRIGGERED".equals(r.getStatus())).toList();
    boolean fullyChecked =
        financialEvents.stream()
                .filter(r -> Set.of("TRIGGERED", "NOT_TRIGGERED").contains(r.getStatus()))
                .map(r -> r.getRuleCode())
                .distinct()
                .count()
            == 3;
    if (!fullyChecked) missing.add("本期利润、现金流及毛利率风险规则尚未全部完成核验");
    boolean risk = exit || Boolean.TRUE.equals(financialRisk) || !currentRules.isEmpty();
    Boolean riskObserved =
        risk ? Boolean.TRUE : financialRisk != null && fullyChecked ? Boolean.FALSE : null;
    String triggeredRules =
        currentRules.stream()
            .map(
                event ->
                    switch (event.getRuleCode()) {
                      case "PROFIT_SLOWDOWN" -> "利润增速放缓";
                      case "CASHFLOW_WARNING" -> "现金流恶化";
                      case "MARGIN_DECLINE" -> "毛利率下降";
                      default -> "待核对财报规则";
                    })
            .distinct()
            .collect(java.util.stream.Collectors.joining("、"));
    var conditions =
        List.of(
            new Condition(
                "最新公开财报",
                financial == null
                    ? "未保存可用财报"
                    : financial.getPeriodEnd() + " · " + financial.getSource(),
                "期末距研究日≤200天且公开时点可核验",
                freshFinancial),
            new Condition(
                "盈利与现金流",
                financial == null
                    ? "未知"
                    : "归母净利润"
                        + value(financial.getNetProfit())
                        + "元；经营现金流"
                        + value(financial.getOperatingCashflow())
                        + "元",
                "均为正；单期表现不证明长期持续性",
                earnings),
            new Condition(
                "实际同比",
                financial == null
                    ? "未知"
                    : "营收同比"
                        + percent(financial.getRevenueYoy())
                        + "；利润同比"
                        + percent(financial.getProfitYoy()),
                "同口径营收和利润同比均为正",
                growth),
            new Condition(
                "长期价格趋势",
                "收盘" + value(study.close()) + "元；MA250=" + value(ma250) + "元",
                "收盘不低于MA250",
                ma250 == null ? null : study.close() >= ma250),
            new Condition(
                "长期合理买价",
                valued
                    ? "用户情景价值"
                        + value(valueLow)
                        + "–"
                        + value(valueHigh)
                        + "元；扣安全边际后观察"
                        + value(entryLow)
                        + "元（保守情景的上限）"
                    : "尚无有效估值假设",
                "当期收盘不高于保守情景扣安全边际后的上限；过低也须重新核验经营风险",
                valued ? study.close() <= entryLow : null),
            new Condition(
                "独立估值证据",
                audit == null
                    ? "尚未核验股本与同行"
                    : (switch (audit.status()) {
                          case "CHECKED_WITH_LIMITS" -> "资料交叉核验完成，仍有边界";
                          case "INCONSISTENT" -> "材料数值不一致";
                          default -> "核验材料未齐";
                        })
                        + "；逐项缺项见估值核验",
                "真实股本、TTM盈利及至少3家有效同行通过交叉检查，仍受方法边界约束",
                audited ? true : null),
            new Condition(
                "核心业务逻辑",
                valued ? thesis.rationale() : "订单、竞争优势与产业证据待核验",
                "用户已核对公开证据且未超过复核日",
                valued ? business : null),
            new Condition(
                "长期退出条件核对",
                exit ? "已触发登记的财报失效条件" : exitClear == null ? "待补条件或财报" : "已核对，未触发",
                "已登记条件且以当期公开财报核验通过",
                exitClear),
            new Condition(
                "财报规则核验",
                fullyChecked ? "当期三项财报风险规则均有记录" : "利润、现金流或毛利率风险规则缺项",
                "当期风险核验齐备",
                fullyChecked ? true : null));
    boolean matched = conditions.stream().allMatch(c -> Boolean.TRUE.equals(c.met()));
    String entryStatus = risk ? "RISK_BLOCKED" : matched ? "CONDITIONAL_MATCH" : "WAIT";
    String entryConclusion =
        !valued
            ? "先保存有公开依据的估值情景及失效条件，再复核长期观察价。"
            : risk
                ? "已有经营或规则风险，暂不新增；估值情景不解除风险拦截。"
                : matched
                    ? "本期行情、财报与用户估值情景条件满足；这仅是可复核候选，须再确认经营假设及个人风险预算。"
                    : "已有用户估值情景，但条件尚未全部满足；逐项核对价格、财报和经营逻辑后再决定。";
    var actions =
        List.of(
            Action.builder()
                .code("LONG_OPEN")
                .title("长期建仓")
                .status(entryStatus)
                .conclusion(entryConclusion)
                .referencePrice(entryLow)
                .referenceLabel("保守估值情景扣安全边际后的观察上限，非保证价值")
                .conditionLogic("ALL")
                .conditions(conditions)
                .invalidation(
                    List.of(valued ? thesis.invalidation() : "盈利质量、竞争优势或估值假设不成立时撤销长期建仓计划。"))
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("LONG_ADD")
                .title("长期加仓")
                .status(entryStatus)
                .conclusion(entryConclusion + "已有持仓不因价格下跌而自动加仓。")
                .referencePrice(entryLow)
                .referenceLabel("保守估值情景扣安全边际后的观察上限，非保证价值")
                .conditionLogic("ALL")
                .conditions(conditions)
                .invalidation(List.of("新增财报或产业证据否定原逻辑时停止加仓。"))
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("LONG_REDUCE")
                .title("长期风险与减仓复核")
                .status(risk || (ma250 != null && study.close() < ma250) ? "WATCH" : "WAIT")
                .conclusion(
                    risk
                        ? "已观察到财报或规则风险，优先核对是否破坏长期逻辑，再决定减仓。"
                        : "按每期财报复核经营逻辑；跌破长期均线只触发趋势复核，不自动证明公司价值消失。")
                .referencePrice(ma250)
                .referenceLabel("250日均线趋势复核价，非估值或保证止损价")
                .conditionLogic("ANY")
                .conditions(
                    List.of(
                        new Condition(
                            "经营风险",
                            "本期财报风险规则："
                                + (triggeredRules.isEmpty() ? "暂无已触发记录" : triggeredRules)
                                + "；非正利润或负现金流："
                                + (financialRisk == null
                                    ? "资料待核验"
                                    : financialRisk ? "已观察到" : "未观察到"),
                            "出现利润、现金流或毛利率风险后复核持续性",
                            riskObserved),
                        new Condition(
                            "长期趋势变化",
                            "收盘" + value(study.close()) + "；MA250=" + value(ma250),
                            "收盘低于MA250，进入趋势复核",
                            ma250 == null ? null : study.close() < ma250)))
                .invalidation(List.of("长期判断不能仅凭一条技术均线或单期财报定性，需核对连续经营证据。"))
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("LONG_EXIT")
                .title("长期逻辑退出")
                .status(exit ? "WATCH" : exitClear == null ? "UNAVAILABLE" : "WAIT")
                .conclusion(
                    exit
                        ? "已触发原情景登记的财报失效条件，优先复核退出。"
                        : exitClear == null ? "补齐已登记的退出条件和所需财报后复核。" : "已核对登记的财报条件，本期继续跟踪。")
                .conditionLogic("ANY")
                .conditions(exitChecks)
                .invalidation(List.of("不能把短期止损触发后改称长期投资，来取消原先的风险计划。"))
                .assumptions(ASSUMPTIONS)
                .build());
    var facts = new ArrayList<String>();
    if (financial != null)
      facts.add(
          "采用已披露累计财报："
              + financial.getPeriodEnd()
              + "，来源"
              + financial.getSource()
              + "；单期正增长不证明未来盈利。");
    facts.add("估值情景=用户年化EPS假设×PE倍数，再扣用户安全边际；不是系统认证价值或历史时点可得性证明。因股本、盈利或行业逻辑变化须重建情景。");
    if (valued) {
      facts.add(
          "情景研究日" + thesis.asOf() + "，最迟复核" + thesis.validUntil() + "；公开依据" + thesis.sourceUrl());
      facts.add("用户确认的失效条件：" + thesis.invalidation());
    }
    if (audited) {
      facts.add(
          "独立核验实际股数 "
              + value(
                  thesis == null || thesis.shareBasis() == null
                      ? null
                      : thesis.shareBasis().currentShares())
              + "，TTM EPS "
              + value(audit.observedTtmEps())
              + " 元，同行市盈率下四分位 "
              + value(audit.peerPeLowerQuartile())
              + " 倍，核验后保守上限 "
              + value(audit.conservativeCap())
              + " 元。");
    }
    exitChecks.forEach(c -> facts.add(c.label() + " · " + c.observed() + " · " + c.required()));
    return Horizon.builder()
        .code("LONG")
        .title("长期经营持有")
        .period("6–24个月")
        .status(risk ? "WATCH" : ma250 == null ? "UNAVAILABLE" : entryStatus)
        .conclusion(entryConclusion)
        .actions(actions)
        .evidence(facts)
        .missing(missing)
        .build();
  }

  /** 校验真正使用的连续交易窗口；不使用未来、混源、缺因子或缺日历的价格。 */
}
