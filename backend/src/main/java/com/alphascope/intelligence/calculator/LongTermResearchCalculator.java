package com.alphascope.intelligence.calculator;

import static com.alphascope.intelligence.calculator.HoldingResearchSupport.ASSUMPTIONS;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.average;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.checkedBars;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.money;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.percent;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.unavailable;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.value;

import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.vo.HoldingResearchView.Action;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import java.util.ArrayList;
import java.util.Comparator;
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
    var cutoff = input.asOf().atTime(16, 10);
    var financial =
        input.stock().financials().stream()
            .filter(
                f ->
                    f.getPeriodEnd() != null
                        && !f.getPeriodEnd().isAfter(input.asOf())
                        && f.getPublishedAt() != null
                        && !f.getPublishedAt().isAfter(cutoff)
                        && !(f.getAvailableAt() != null
                                ? f.getAvailableAt()
                                : "DAY".equals(f.getPublicationPrecision())
                                    ? f.getPublishedAt().toLocalDate().plusDays(1).atStartOfDay()
                                    : f.getPublishedAt())
                            .isAfter(cutoff))
            .max(
                Comparator.comparing(FinancialReport::getPeriodEnd)
                    .thenComparing(f -> f.getVersion() == null ? 0 : f.getVersion()))
            .orElse(null);
    boolean freshFinancial =
        financial != null && !financial.getPeriodEnd().isBefore(input.asOf().minusDays(200));
    if (!freshFinancial) missing.add("最近200天内的有效累计财报期末与公开来源");
    if (financial == null || financial.getNetProfit() == null) missing.add("已披露归母净利润");
    if (financial == null || financial.getOperatingCashflow() == null) missing.add("已披露经营现金流");
    if (financial == null || financial.getRevenueYoy() == null || financial.getProfitYoy() == null)
      missing.add("同口径营收与利润同比");
    missing.add("可核验的估值合理区间及安全边际，尚未接入");
    missing.add("竞争优势、订单与产业逻辑的人工核验，尚未完成");
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
    boolean risk = Boolean.TRUE.equals(financialRisk) || !currentRules.isEmpty();
    Boolean riskObserved =
        risk ? Boolean.TRUE : financialRisk != null && fullyChecked ? Boolean.FALSE : null;
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
            new Condition("长期合理买价", "尚无已核验估值区间", "先核对盈利质量、估值与安全边际，再制定分批执行价", null),
            new Condition("核心业务逻辑", "订单、竞争优势与产业证据待核验", "由公开证据确认经营逻辑仍成立", null));
    String entryStatus = risk ? "RISK_BLOCKED" : "WAIT";
    var actions =
        List.of(
            Action.builder()
                .code("LONG_OPEN")
                .title("长期建仓")
                .status(entryStatus)
                .conclusion("估值和核心逻辑未核实，当前不形成长期合理买入价；先完成缺项，再用技术条件选择执行时机。")
                .conditionLogic("ALL")
                .conditions(conditions)
                .invalidation(List.of("盈利质量、竞争优势或估值假设不成立时撤销长期建仓计划。"))
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("LONG_ADD")
                .title("长期加仓")
                .status(entryStatus)
                .conclusion("已有持仓也须重新核验估值与原逻辑；不因价格下跌而默认加仓。当前缺项未清除，等待复核。")
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
                            "本期已触发财报规则"
                                + currentRules.size()
                                + "项；非正利润或负现金流="
                                + (financialRisk == null ? "待核验" : financialRisk),
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
                .status("WAIT")
                .conclusion("确认核心业务逻辑失效、估值假设被否定或个人原定风险底线到达时，执行原退出计划；系统尚未核实这些个人与经营前提。")
                .conditionLogic("ANY")
                .conditions(
                    List.of(
                        new Condition("核心逻辑失效", "待核验", "持续经营证据确认原投资逻辑被否定", null),
                        new Condition("个人风险底线", "未读取个人成本和风险预算", "达到原先明确的个人退出条件", null)))
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
    facts.add("长期方向要逐期核对财报、现金流、产业逻辑与估值；本产品尚未核实合理价值和个人投资论点。");
    return Horizon.builder()
        .code("LONG")
        .title("长期经营持有")
        .period("6–24个月")
        .status(risk ? "WATCH" : ma250 == null ? "UNAVAILABLE" : "WAIT")
        .conclusion(risk ? "先复核经营风险，当前不支持新增长期风险。" : "长期建仓与加仓等待估值和经营逻辑核验；已有持仓按下列证据和退出前提逐期检查。")
        .actions(actions)
        .evidence(facts)
        .missing(missing)
        .build();
  }

  /** 校验真正使用的连续交易窗口；不使用未来、混源、缺因子或缺日历的价格。 */
}
