package com.alphascope.intelligence.calculator;

import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.HoldingResearchView.Action;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 同一证据下的多周期行动研究；复用短线策略，中线核对65期趋势，长线分开核对财报与估值缺项。
 *
 * <p>1–20交易日、1–6个月、6–24个月是本产品观察期限。全部参数均为未完成效果验证的研究假设。
 *
 * @author chenxuefeng
 */
public final class HoldingResearchCalculator {
  public static final String VERSION = "HOLDING_RESEARCH_V1";
  private static final List<String> ASSUMPTIONS =
      List.of(
          "行情条件满足后仍需确认个人计划、资金与可承受损失；本视图不读取个人持仓或给出数量比例。",
          "参考位按收盘证据核对，跳空、涨跌停、费用和实际成交可能使执行价格偏离参考价。",
          "买入与加仓须分别满足条件；新增风险优先于继续买入，不能把短期交易变成长持来回避退出条件。");

  private HoldingResearchCalculator() {}

  /**
   * 只使用截止日及以前的同源证据，不发起网络请求、改变账户或重建冻结报告。
   *
   * @param input 有明确日期、日历、财报可用时点和演示标志的研究输入
   * @return 三个独立周期及其证据、买卖条件和实际缺项
   */
  public static HoldingResearchView calculate(HoldingResearchInput input) {
    var stock = input.stock();
    var study =
        PriceVolumeCalculator.calculate(
            PriceVolumeWindow.builder()
                .symbol(stock.symbol())
                .name(stock.name())
                .date(input.asOf())
                .bars(stock.history())
                .expectedDates(input.expectedDates())
                .suspended(stock.suspended())
                .synthetic(input.synthetic())
                .build());
    var quote =
        stock.history().stream()
            .filter(b -> b.getTradeDate() != null && !b.getTradeDate().isAfter(input.asOf()))
            .max(Comparator.comparing(MarketBarDO::getTradeDate))
            .orElse(null);
    return HoldingResearchView.builder()
        .version(VERSION)
        .symbol(stock.symbol())
        .name(stock.name())
        .asOf(input.asOf())
        .quoteDate(quote == null ? null : quote.getTradeDate())
        .source(study.source())
        .close(study.close())
        .priceVolume(study)
        .horizons(List.of(shortTerm(study), mediumTerm(input, study), longTerm(input, study)))
        .notes(
            List.of(
                "本页读取当前保存版本；查看历史日期属于当前版本回顾，不证明历史时点可得。冻结报告保留原修订。",
                "短期关注量价和结构；中期关注趋势与回撤；长期关注经营、现金流、估值和核心逻辑。各周期不相互替代。",
                "长期估值与竞争优势尚需人工核实，不把技术观察带标成长期合理价值。方法尚未完成历史效果验证。"))
        .build();
  }

  private static Horizon shortTerm(PriceVolumeStudy study) {
    if (!"AVAILABLE".equals(study.status()) || study.strategies() == null) {
      return unavailable(
          "SHORT", study.missing().isEmpty() ? "当日量价资料不可用" : String.join("；", study.missing()));
    }
    var actions =
        study.strategies().stream()
            .map(
                plan ->
                    Action.builder()
                        .code(plan.code())
                        .title(plan.title())
                        .status(plan.status())
                        .conclusion(plan.conclusion())
                        .observationZone(plan.observationZone())
                        .referencePrice(
                            plan.referenceZone() == null ? null : plan.referenceZone().upper())
                        .referenceLabel("提前形成的原参考区上沿")
                        .stopReference(plan.stopReference())
                        .conditionLogic(plan.conditionLogic())
                        .conditions(plan.conditions())
                        .invalidation(plan.invalidation())
                        .assumptions(plan.assumptions())
                        .build())
            .toList();
    String status =
        actions.stream().anyMatch(a -> "WATCH".equals(a.status()))
            ? "WATCH"
            : actions.stream().anyMatch(a -> "CONDITIONAL_MATCH".equals(a.status()))
                ? "CONDITIONAL_MATCH"
                : actions.stream().anyMatch(a -> "RISK_BLOCKED".equals(a.status()))
                    ? "RISK_BLOCKED"
                    : "WAIT";
    return Horizon.builder()
        .code("SHORT")
        .title("短期交易")
        .period("1–20个交易日")
        .status(status)
        .conclusion(
            "WATCH".equals(status)
                ? "先复核减仓或风险退出，暂停新增风险；具体触发见下列方案。"
                : "CONDITIONAL_MATCH".equals(status)
                    ? "有方案满足本期行情条件，核对观察带、失效条件和个人前提后再决定。"
                    : "等待独立买入条件；当前没有买入确认，按已列价带与量能要求跟踪。")
        .actions(actions)
        .evidence(study.evidence())
        .missing(List.of())
        .build();
  }

  private static Horizon mediumTerm(HoldingResearchInput input, PriceVolumeStudy study) {
    if (!"AVAILABLE".equals(study.status()))
      return unavailable("MEDIUM", "需先补齐当日近两月行情与日历，才能评估中期行动");
    List<Bar> bars;
    try {
      bars = checkedBars(input, 65);
    } catch (IllegalArgumentException gap) {
      return unavailable("MEDIUM", gap.getMessage());
    }
    int n = bars.size();
    Bar last = bars.getLast();
    double ma60 = average(bars.subList(n - 60, n));
    double earlierMa60 = average(bars.subList(n - 65, n - 5));
    double previousMa60 = average(bars.subList(n - 61, n - 1));
    var prior = bars.subList(n - 61, n - 1);
    double low = prior.stream().mapToDouble(Bar::low).min().orElseThrow();
    double high = prior.stream().mapToDouble(Bar::high).max().orElseThrow();
    double pad = Math.max(ma60 * .01, study.atr14() * .25);
    double stop = money(low - study.atr14() * .5);
    if (stop <= 0) return unavailable("MEDIUM", "60期原结构无法形成正数退出参考");
    var band =
        new PriceVolumeStudy.Zone(
            money(Math.max(.01, ma60 - pad)),
            money(ma60 + pad),
            "MA60_RETEST",
            List.of(input.asOf()),
            null);
    boolean risk = (band.upper() - stop) / band.upper() > .12 || stop >= band.lower();
    boolean exit = last.close() <= stop || (last.close() < ma60 && ma60 < earlierMa60);
    boolean pressureExists = high >= last.close();
    double pressure = money(high * .99);
    boolean reduce =
        pressureExists
            && last.close() >= pressure
            && study.return5() >= .02
            && study.volumeRatio5() <= .8;
    var entry =
        List.of(
            new Condition(
                "中期趋势",
                "收盘"
                    + value(last.close())
                    + "，MA20 "
                    + value(study.ma20())
                    + "，MA60 "
                    + value(ma60),
                "收盘及MA20均不低于MA60",
                last.close() >= ma60 && study.ma20() >= ma60),
            new Condition(
                "均线方向",
                "MA60 " + value(ma60) + "；5个交易日前 " + value(earlierMa60),
                "MA60不下降",
                ma60 >= earlierMa60),
            new Condition(
                "回踩观察带",
                "本期收盘" + value(last.close()),
                "收盘处于" + value(band.lower()) + "–" + value(band.upper()) + "元",
                last.close() >= band.lower() && last.close() <= band.upper()),
            new Condition(
                "技术风险过滤",
                value((band.upper() - stop) / band.upper() * 100) + "%",
                "观察带上沿至原退出参考距离>0且≤12%；未触发退出或减仓组合",
                !risk && !exit && !reduce));
    var add = new ArrayList<>(entry);
    add.add(
        new Condition(
            "连续确认与量能",
            "前收" + value(bars.get(n - 2).close()) + "；当日成交额量比" + value(study.volumeRatio()),
            "前收不低于当时MA60、本期不低于MA60且当日量比≥1",
            bars.get(n - 2).close() >= previousMa60
                && last.close() >= ma60
                && study.volumeRatio() >= 1));
    var sell =
        List.of(
            new Condition(
                "上涨后缩量遇压",
                "前60期高点"
                    + value(high)
                    + "；5日收益"
                    + value(study.return5() * 100)
                    + "%；5日量比"
                    + value(study.volumeRatio5()),
                "未突破前60期高点，接近其99%以上，5日收益≥2%且5日量比≤0.8",
                reduce));
    var stopConditions =
        List.of(
            new Condition(
                "原结构风险退出",
                "收盘" + value(last.close()),
                "收盘≤原60期最低价减0.5×ATR14：" + value(stop) + "元",
                last.close() <= stop),
            new Condition(
                "趋势失效复核",
                "收盘与MA60方向见中期证据",
                "收盘低于MA60且MA60低于5个交易日前",
                last.close() < ma60 && ma60 < earlierMa60));
    var invalidation =
        List.of(
            "退出组合出现时优先降低风险，撤销新增方案。",
            "MA60及观察带随新交易日更新，不能将本期均线当成固定委托价格。",
            "12%为未验证技术过滤参数，不能替代个人亏损预算或通过降低退出位凑条件。");
    var actions =
        List.of(
            Action.builder()
                .code("MEDIUM_OPEN")
                .title("中期回踩建仓")
                .status(exit || risk || reduce ? "RISK_BLOCKED" : state(entry, "ALL"))
                .conclusion(
                    exit || risk || reduce
                        ? "当前风险过滤阻止新增；先处理退出或减仓复核。"
                        : "CONDITIONAL_MATCH".equals(state(entry, "ALL"))
                            ? "本期趋势、回踩价带和风险过滤均已满足，确认个人前提后再决定执行。"
                            : "等待趋势、回踩价带与风险过滤同时成立；当前未满足项见条件。")
                .observationZone(band)
                .referencePrice(money(ma60))
                .referenceLabel("本期60日均线")
                .stopReference(stop)
                .conditionLogic("ALL")
                .conditions(entry)
                .invalidation(invalidation)
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("MEDIUM_ADD")
                .title("中期确认后加仓")
                .status(exit || risk || reduce ? "RISK_BLOCKED" : state(add, "ALL"))
                .conclusion("只用于已有持仓且原逻辑仍成立的情形；等待连续收复与量能确认，不用于下跌摊薄成本。")
                .observationZone(band)
                .referencePrice(money(ma60))
                .referenceLabel("本期60日均线")
                .stopReference(stop)
                .conditionLogic("ALL")
                .conditions(add)
                .invalidation(invalidation)
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("MEDIUM_REDUCE")
                .title("中期遇压减仓")
                .status(state(sell, "ANY"))
                .conclusion(
                    pressureExists ? "达到压力附近且缩量反弹时，已有持仓优先复核分批减仓。" : "已越过此前高点，暂无该窗口上方压力，不外推卖出目标价。")
                .referencePrice(pressureExists ? pressure : null)
                .referenceLabel("前60期高点的99%压力观察线")
                .stopReference(stop)
                .conditionLogic("ANY")
                .conditions(sell)
                .invalidation(List.of("放量突破后重评，不继续沿用旧压力作固定卖价。"))
                .assumptions(ASSUMPTIONS)
                .build(),
            Action.builder()
                .code("MEDIUM_EXIT")
                .title("中期退出复核")
                .status(state(stopConditions, "ANY"))
                .conclusion(exit ? "已有失效组合，优先复核退出或减仓，撤销中期新建与加仓计划。" : "未触发本方法退出组合，继续核对动态均线和原结构。")
                .referencePrice(money(ma60))
                .referenceLabel("动态趋势复核价MA60")
                .stopReference(stop)
                .conditionLogic("ANY")
                .conditions(stopConditions)
                .invalidation(invalidation)
                .assumptions(ASSUMPTIONS)
                .build());
    String status = exit || reduce ? "WATCH" : risk ? "RISK_BLOCKED" : state(entry, "ALL");
    return Horizon.builder()
        .code("MEDIUM")
        .title("中期趋势持有")
        .period("1–6个月")
        .status(status)
        .conclusion(
            exit
                ? "趋势或原结构出现失效组合，优先处理退出复核。"
                : risk
                    ? "观察价带到退出参考的距离超出技术过滤，暂不新增。"
                    : "CONDITIONAL_MATCH".equals(status)
                        ? "本期中期回踩行情条件满足，仍需确认个人前提。"
                        : "等待中期趋势与回踩条件，已有持仓按压力和失效组合复核。")
        .actions(actions)
        .evidence(
            List.of(
                "MA60=" + value(ma60) + "元；5个交易日前MA60=" + value(earlierMa60) + "元。",
                "前60期最高=" + value(high) + "元，最低=" + value(low) + "元，均不含本期。",
                "价格按本期因子锚点复权；当前退出参考不纳入本期新低。"))
        .missing(List.of())
        .build();
  }

  private static Horizon longTerm(HoldingResearchInput input, PriceVolumeStudy study) {
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
  private static List<Bar> checkedBars(HoldingResearchInput input, int count) {
    var eligible =
        input.stock().history().stream()
            .filter(b -> b.getTradeDate() != null && !b.getTradeDate().isAfter(input.asOf()))
            .sorted(Comparator.comparing(MarketBarDO::getTradeDate))
            .toList();
    if (eligible.size() < count)
      throw new IllegalArgumentException("需至少" + count + "个有效交易日，当前仅" + eligible.size() + "日");
    var tail = eligible.subList(eligible.size() - count, eligible.size());
    if (!tail.getLast().getTradeDate().equals(input.asOf()))
      throw new IllegalArgumentException("缺少本期行情");
    var sources = tail.stream().map(MarketBarDO::getSource).collect(Collectors.toSet());
    if (sources.size() != 1 || sources.contains(null) || sources.contains(""))
      throw new IllegalArgumentException("研究窗口行情与因子来源不一致");
    var dates = tail.stream().map(MarketBarDO::getTradeDate).collect(Collectors.toSet());
    var required =
        input.expectedDates().stream()
            .filter(d -> !d.isBefore(tail.getFirst().getTradeDate()) && !d.isAfter(input.asOf()))
            .collect(Collectors.toSet());
    if (dates.size() != tail.size() || !dates.equals(required))
      throw new IllegalArgumentException("研究窗口缺少连续交易日或日历，不能跳过缺日计算均线");
    double anchor = positive(tail.getLast().getFactor());
    var bars = new ArrayList<Bar>();
    for (var row : tail) {
      if (!Boolean.FALSE.equals(row.getSynthetic()))
        throw new IllegalArgumentException("窗口缺少真实模式证明");
      double ratio = positive(row.getFactor()) / anchor;
      double close = positive(row.getClose()) * ratio,
          low = positive(row.getLow()) * ratio,
          high = positive(row.getHigh()) * ratio;
      if (!Double.isFinite(close + low + high)
          || close <= 0
          || low <= 0
          || high <= 0
          || low > high
          || close < low
          || close > high) throw new IllegalArgumentException("历史OHLC关系无效");
      bars.add(new Bar(row.getTradeDate(), close, low, high));
    }
    return List.copyOf(bars);
  }

  private static Horizon unavailable(String code, String reason) {
    return Horizon.builder()
        .code(code)
        .title("SHORT".equals(code) ? "短期交易" : "MEDIUM".equals(code) ? "中期趋势持有" : "长期经营持有")
        .period("SHORT".equals(code) ? "1–20个交易日" : "MEDIUM".equals(code) ? "1–6个月" : "6–24个月")
        .status("UNAVAILABLE")
        .conclusion("补齐证据后再形成该周期的买卖计划；当前不输出可执行价位。")
        .actions(List.of())
        .evidence(List.of())
        .missing(List.of(reason))
        .build();
  }

  private static String state(List<Condition> conditions, String logic) {
    if ("ANY".equals(logic))
      return conditions.stream().anyMatch(c -> Boolean.TRUE.equals(c.met())) ? "WATCH" : "WAIT";
    return !conditions.isEmpty() && conditions.stream().allMatch(c -> Boolean.TRUE.equals(c.met()))
        ? "CONDITIONAL_MATCH"
        : "WAIT";
  }

  private static double average(List<Bar> bars) {
    return bars.stream().mapToDouble(Bar::close).average().orElseThrow();
  }

  private static double positive(Number value) {
    if (value == null || !Double.isFinite(value.doubleValue()) || value.doubleValue() <= 0)
      throw new IllegalArgumentException("历史价格或因子无效");
    return value.doubleValue();
  }

  private static double money(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private static String value(Number value) {
    return value == null ? "未知" : String.format(Locale.ROOT, "%.2f", value.doubleValue());
  }

  private static String percent(Number value) {
    return value == null ? "未知" : HoldingResearchCalculator.value(value.doubleValue() * 100) + "%";
  }

  /**
   * 同一截止日因子锚点的内部价格，不对外暴露。
   *
   * @author chenxuefeng
   */
  private record Bar(LocalDate date, double close, double low, double high) {}
}
