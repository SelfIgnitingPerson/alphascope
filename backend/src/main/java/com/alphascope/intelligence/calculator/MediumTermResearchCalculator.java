package com.alphascope.intelligence.calculator;

import static com.alphascope.intelligence.calculator.HoldingResearchSupport.ASSUMPTIONS;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.average;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.checkedBars;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.money;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.state;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.unavailable;
import static com.alphascope.intelligence.calculator.HoldingResearchSupport.value;

import com.alphascope.intelligence.calculator.HoldingResearchSupport.Bar;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.vo.HoldingResearchView.Action;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.HoldingResearchView.MediumTrendCandidate;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import java.util.ArrayList;
import java.util.List;

/**
 * 中期趋势与条件判断；保持原冻结算法口径。
 *
 * @author chenxuefeng
 */
public final class MediumTermResearchCalculator {
  private MediumTermResearchCalculator() {}

  /** 用现有研究条件逐项形成中期观察，供页面和回放共用。 */
  public static Horizon calculate(HoldingResearchInput input, PriceVolumeStudy study) {
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

  /** MA60 对照的可复算条件，基于与现有中期规则相同的连续同源窗口。 */
  public static TrendConditions trendConditions(
      HoldingResearchInput input, PriceVolumeStudy study) {
    if (!"AVAILABLE".equals(study.status())) return null;
    List<Bar> bars;
    try {
      bars = checkedBars(input, 65);
    } catch (IllegalArgumentException gap) {
      return null;
    }
    int n = bars.size();
    double ma = average(bars.subList(n - 60, n));
    double oldMa = average(bars.subList(n - 65, n - 5));
    double previousMa = average(bars.subList(n - 61, n - 1));
    double close = bars.getLast().close();
    double pad = Math.max(ma * .01, study.atr14() * .25);
    double upper = money(ma + pad);
    double originalStop =
        money(
            bars.subList(n - 61, n - 1).stream().mapToDouble(Bar::low).min().orElseThrow()
                - study.atr14() * .5);
    double sizingStop = money(ma - study.atr14());
    return new TrendConditions(
        ma >= oldMa && close >= ma && bars.get(n - 2).close() >= previousMa,
        close <= upper,
        study.volumeRatio() >= 1,
        originalStop > 0 && originalStop < ma - pad && (upper - originalStop) / upper <= .12,
        close < ma && ma < oldMa,
        sizingStop > 0 && sizingStop < close ? sizingStop : null,
        money(ma));
  }

  /**
   * 读取与历史对照相同的中期顺势条件，供当前个股研究显示。
   *
   * @param input 当日同源价格与日历证据
   * @param study 同日量价计算结果
   * @return 当前趋势状态、均线和用于计算仓位的风险参考
   */
  public static MediumTrendCandidate trendCandidate(
      HoldingResearchInput input, PriceVolumeStudy study) {
    var conditions = trendConditions(input, study);
    if (conditions == null) return new MediumTrendCandidate(false, false, null, null, false);
    return new MediumTrendCandidate(
        true, conditions.trend(), conditions.ma60(), conditions.sizingStop(), conditions.exit());
  }

  /** 分步比较时的趋势、回踩、量能、风险和退出条件。 @author chenxuefeng */
  public record TrendConditions(
      boolean trend,
      boolean retest,
      boolean volume,
      boolean structureRiskAllowed,
      boolean exit,
      Double sizingStop,
      Double ma60) {}
}
