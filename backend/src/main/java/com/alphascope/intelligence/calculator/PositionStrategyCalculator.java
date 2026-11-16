package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.dto.PositionStrategyInput;
import com.alphascope.intelligence.vo.PositionStrategy;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.intelligence.vo.PriceVolumeStudy.Zone;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 固定条件式仓位研究；不读取个人仓位，不将“未触发减仓”反推为买入条件。
 *
 * <p>先验证数据和止损保护，再分别评估建仓、加仓与减仓；8%距离和3%追价上限均为未验证研究过滤线。
 *
 * @author chenxuefeng
 */
public final class PositionStrategyCalculator {
  public static final String VERSION = "POSITION_RESEARCH_V1";
  private static final double MAX_RISK = 0.08;
  private static final double MAX_CHASE = 0.03;
  private static final List<String> ASSUMPTIONS =
      List.of(
          "自选研究未核实个人资金、持仓、成本和风险承受度；行情条件满足仍需人工确认。",
          "候选区间不是保证成交价，不提供建仓、加仓或减仓数量与比例。",
          "8%是候选价带到结构止损的技术过滤距离，不是个人可承担亏损；参数效果尚未验证。");

  private PositionStrategyCalculator() {}

  /**
   * 从同一份冻结量价事实及提前形成的区间生成四类策略，不访问行情服务或持仓库。
   *
   * @param input 已验证的价格、量能和参考区间时点
   * @return 按回踩建仓、突破建仓、回踩加仓、减仓排序的方案
   */
  public static List<PositionStrategy> calculate(PositionStrategyInput input) {
    var study = input.study();
    if (!"AVAILABLE".equals(study.status())
        || !"TURNOVER_AMOUNT".equals(study.volumeBasis())
        || study.close() == null
        || study.ma20() == null
        || study.return5() == null
        || study.volumeRatio() == null
        || study.volumeRatio5() == null
        || input.weakSelling() == null
        || input.shrinkingRally() == null) {
      return unavailable(study);
    }
    if (input.breakoutReferenceAsOf() == null
        || !input.breakoutReferenceAsOf().isBefore(study.asOf())
        || input.addReferenceAsOf() == null
        || !input.addReferenceAsOf().isBefore(input.breakoutReferenceAsOf())) {
      return unavailable(study);
    }
    if (!visibleAt(input.breakoutReference(), input.breakoutReferenceAsOf())
        || !visibleAt(input.addReference(), input.addReferenceAsOf())) {
      return unavailable(study);
    }
    var pullback = new ArrayList<Condition>();
    Zone support = study.support();
    if (support != null) {
      pullback.add(
          condition(
              "回踩后收复支撑",
              "最低" + money(input.currentLow()) + "，收盘" + money(study.close()),
              "最低不高于支撑上沿，收盘回到上沿至上沿+2%之间",
              input.currentLow() <= support.upper()
                  && study.close() >= support.upper()
                  && study.close() <= support.upper() * 1.02));
    }
    pullback.add(
        condition(
            "回踩成交额不过度放大",
            "当日成交额量比" + value(study.volumeRatio()),
            "当日量比≤1.00",
            study.volumeRatio() <= 1));
    pullback.add(
        condition("收盘企稳", "当日收益" + percent(input.dayReturn()), "当日收益≥0", input.dayReturn() >= 0));
    Zone pullbackBand = support == null ? null : entryBand(support, 0.02, study.stopReference());

    Zone breakout = input.breakoutReference();
    var opening = new ArrayList<Condition>();
    if (breakout != null) {
      opening.add(
          condition(
              "首次收盘突破原压力",
              "前收"
                  + money(input.previousClose())
                  + "，收盘"
                  + money(study.close())
                  + "；参考截至"
                  + input.breakoutReferenceAsOf(),
              "前收≤" + money(breakout.upper()) + "且本期收盘>该上沿",
              input.previousClose() <= breakout.upper() && study.close() > breakout.upper()));
      opening.add(
          condition(
              "限制追价",
              "收盘" + money(study.close()),
              "不高于原压力上沿+3%：" + money(breakout.upper() * (1 + MAX_CHASE)),
              study.close() <= breakout.upper() * (1 + MAX_CHASE)));
    }
    opening.add(
        condition(
            "突破成交额确认",
            "当日成交额量比" + value(study.volumeRatio()),
            "当日量比≥1.20",
            study.volumeRatio() >= 1.2));
    opening.add(
        condition("突破日非下跌", "当日收益" + percent(input.dayReturn()), "当日收益>0", input.dayReturn() > 0));
    Zone breakoutBand =
        breakout == null ? null : entryBand(breakout, MAX_CHASE, study.stopReference());

    Zone add = input.addReference();
    var adding = new ArrayList<Condition>();
    if (add != null) {
      adding.add(
          condition(
              "连续两次收盘守住原压力上沿",
              "前收"
                  + money(input.previousClose())
                  + "，收盘"
                  + money(study.close())
                  + "；参考截至"
                  + input.addReferenceAsOf(),
              "两次收盘均>" + money(add.upper()),
              input.previousClose() > add.upper() && study.close() > add.upper()));
      adding.add(
          condition(
              "回踩突破位后收复且不追高",
              "最低" + money(input.currentLow()) + "，收盘" + money(study.close()),
              "最低≤上沿+2%，收盘介于上沿和上沿+3%",
              input.currentLow() <= add.upper() * 1.02
                  && study.close() > add.upper()
                  && study.close() <= add.upper() * (1 + MAX_CHASE)));
    }
    adding.add(
        condition(
            "量能未明显退潮",
            "5日成交额量比" + value(study.volumeRatio5()) + "，当日" + value(study.volumeRatio()),
            "5日量比及当日量比均≥1.00",
            study.volumeRatio5() >= 1 && study.volumeRatio() >= 1));
    adding.add(
        condition(
            "回踩日收盘企稳", "当日收益" + percent(input.dayReturn()), "当日收益≥0", input.dayReturn() >= 0));
    Zone addBand = add == null ? null : entryBand(add, MAX_CHASE, study.stopReference());

    return List.of(
        entry(input, new Draft("OPEN_PULLBACK", "回踩企稳建仓", pullbackBand, support, pullback)),
        entry(input, new Draft("OPEN_BREAKOUT", "放量突破建仓", breakoutBand, breakout, opening)),
        entry(input, new Draft("ADD_RETEST", "突破确认后回踩加仓", addBand, add, adding)),
        reduction(input));
  }

  /**
   * 缺少完整量价输入时冻结四类“资料不足”方案，不输出买卖观察带。
   *
   * @param study 停牌、演示、陈旧或缺证据的冻结研究
   * @return 四个明确不可评估的方案
   */
  public static List<PositionStrategy> unavailable(PriceVolumeStudy study) {
    String reason =
        (study.missing() == null || study.missing().isEmpty())
            ? "缺少提前形成且可核验的参考区间或完整量价事实"
            : String.join("；", study.missing());
    return List.of("OPEN_PULLBACK", "OPEN_BREAKOUT", "ADD_RETEST", "REDUCE").stream()
        .map(
            code ->
                PositionStrategy.builder()
                    .version(VERSION)
                    .code(code)
                    .title(title(code))
                    .status("UNAVAILABLE")
                    .conditionLogic(code.equals("REDUCE") ? "ANY" : "ALL")
                    .conclusion("资料不足，暂不形成此类策略判断。")
                    .conditions(List.of(condition("输入与参考时点", reason, "完整、同源、可核验且不使用确认日之后资料", null)))
                    .invalidation(List.of("先补齐当前明确缺项，再生成新修订；不能把未知当作未触发。"))
                    .assumptions(ASSUMPTIONS)
                    .build())
        .toList();
  }

  private static PositionStrategy entry(PositionStrategyInput input, Draft draft) {
    var study = input.study();
    var conditions = new ArrayList<>(draft.conditions());
    boolean hasStop =
        study.stopReference() != null && study.stopReference() > 0 && draft.band() != null;
    Double risk = hasStop ? riskDistance(draft.band(), study.stopReference()) : null;
    if (draft.band() != null) {
      conditions.add(
          condition(
              "收盘位于候选观察带",
              "本期收盘" + money(study.close()),
              money(draft.band().lower()) + "至" + money(draft.band().upper()),
              study.close() >= draft.band().lower() && study.close() <= draft.band().upper()));
    }
    boolean riskPass = risk != null && risk > 0 && risk <= MAX_RISK;
    boolean defensive =
        List.of("REDUCE_REVIEW", "PROFIT_PROTECTION_REVIEW").contains(study.decision());
    conditions.add(condition("不与已触发减仓条件冲突", study.conclusion(), "未触发明确减仓或遇压保护组合", !defensive));
    conditions.add(
        condition(
            "趋势过滤",
            "收盘" + money(study.close()) + "，MA20=" + money(study.ma20()),
            "收盘≥MA20",
            study.close() >= study.ma20()));
    conditions.add(
        condition(
            "盘中未触及结构止损",
            "本期最低" + money(input.currentLow()) + "，止损参考" + money(study.stopReference()),
            "最低价>止损参考",
            hasStop ? input.currentLow() > study.stopReference() : null));
    conditions.add(
        condition(
            "候选区间风险距离",
            risk == null ? "没有有效止损或候选价带" : percent(risk),
            "从候选区间上沿至止损的距离>0且≤8%（技术过滤，不是个人亏损预算）",
            risk == null ? null : riskPass));
    String status;
    String conclusion;
    if (!hasStop || draft.reference() == null) {
      status = "UNAVAILABLE";
      conclusion = "没有有效原结构参考，暂不形成此类建仓或加仓条件。";
    } else if (defensive || !riskPass || input.currentLow() <= study.stopReference()) {
      status = "RISK_BLOCKED";
      conclusion = "当前触发风险过滤，不形成建仓或加仓建议；不得通过放宽止损绕过条件。";
    } else if (conditions.stream().allMatch(c -> Boolean.TRUE.equals(c.met()))) {
      status = "CONDITIONAL_MATCH";
      conclusion = "本期行情条件已满足，可进入人工复核；仍需确认适用持仓状态和个人风险额度，不能直接下单。";
    } else {
      status = "WAIT";
      conclusion = "条件尚未全部满足，等待明确的价格、成交额或趋势确认，不把“未减仓”当作买入信号。";
    }
    var assumptions = new ArrayList<>(ASSUMPTIONS);
    assumptions.add(
        draft.code().equals("ADD_RETEST")
            ? "仅适用于已经持有、原建仓逻辑仍有效且尚有个人风险额度；系统未核实是否持有，不用于下跌摊薄成本。"
            : "仅研究尚未建仓时的候选方案；系统未核实是否空仓，不能与加仓方案混用。");
    return PositionStrategy.builder()
        .version(VERSION)
        .code(draft.code())
        .title(draft.title())
        .status(status)
        .conditionLogic("ALL")
        .conclusion(conclusion)
        .observationZone(hasStop ? draft.band() : null)
        .referenceZone(draft.reference())
        .stopReference(study.stopReference())
        .referenceRisk(risk)
        .maximumReferenceRisk(MAX_RISK)
        .conditions(List.copyOf(conditions))
        .invalidation(
            List.of(
                "收盘跌回原参考区下沿，或盘中触及结构止损，暂停此方案并重新评估。", "新增K线后重新核对MA20、成交额与区间；不能把本期候选价格带当作长期有效限价单。"))
        .assumptions(List.copyOf(assumptions))
        .build();
  }

  private static PositionStrategy reduction(PositionStrategyInput input) {
    var s = input.study();
    boolean weak = Boolean.TRUE.equals(input.weakSelling());
    boolean shrinking = Boolean.TRUE.equals(input.shrinkingRally());
    var conditions =
        List.of(
            condition(
                "收盘触及结构止损",
                "收盘" + money(s.close()) + "，止损参考" + money(s.stopReference()),
                "收盘≤有效止损参考",
                input.stopTriggered()),
            condition(
                "放量走弱组合",
                "当日收益"
                    + percent(input.dayReturn())
                    + "、量比"
                    + value(s.volumeRatio())
                    + "；5日收益"
                    + percent(s.return5())
                    + "、量比"
                    + value(s.volumeRatio5()),
                "放量下跌失守支撑，或5日跌幅≥2%、5日量比≥1.2且低于MA20",
                weak),
            condition(
                "缩量反弹遇压",
                "5日收益" + percent(s.return5()) + "、成交额量比" + value(s.volumeRatio5()),
                "接近压力、5日涨幅≥2%且5日量比≤0.8",
                shrinking));
    String status =
        conditions.stream().anyMatch(c -> Boolean.TRUE.equals(c.met()))
            ? "CONDITIONAL_MATCH"
            : "WATCH_REDUCTION".equals(s.decision()) ? "WATCH" : "WAIT";
    var assumptions = new ArrayList<>(ASSUMPTIONS);
    assumptions.add("减仓仅适用于已有仓位；系统未核实是否持有，空仓者不执行此方案。");
    return PositionStrategy.builder()
        .version(VERSION)
        .code("REDUCE")
        .title("减仓与风险退出")
        .status(status)
        .conditionLogic("ANY")
        .conclusion(s.conclusion())
        .stopReference(s.stopReference())
        .referenceZone(s.support())
        .conditions(conditions)
        .invalidation(
            List.of("若重新站回原支撑或放量突破压力，重新核验组合；不因一次反弹就自动撤销个人既定风险计划。", "系统按收盘核验组合，盘中穿越和实际委托执行需另行核实。"))
        .assumptions(List.copyOf(assumptions))
        .build();
  }

  private static boolean visibleAt(Zone zone, LocalDate cutoff) {
    return zone != null
        && zone.dates() != null
        && !zone.dates().isEmpty()
        && zone.dates().stream().allMatch(date -> date != null && !date.isAfter(cutoff));
  }

  private static double riskDistance(Zone band, double stop) {
    return BigDecimal.valueOf(band.upper())
        .subtract(BigDecimal.valueOf(stop))
        .divide(BigDecimal.valueOf(band.upper()), 8, RoundingMode.HALF_UP)
        .doubleValue();
  }

  /** 候选上沿同时受追价与风险限制；无法形成非空安全区间时保留原带，由风险过滤明确拦截。 */
  private static Zone entryBand(Zone reference, double premium, Double stop) {
    double lower = reference.upper();
    double upper =
        BigDecimal.valueOf(lower * (1 + premium)).setScale(2, RoundingMode.DOWN).doubleValue();
    if (stop != null && stop > 0) {
      double cap =
          BigDecimal.valueOf(stop)
              .divide(BigDecimal.valueOf(1 - MAX_RISK), 2, RoundingMode.DOWN)
              .doubleValue();
      if (cap >= lower) {
        upper = Math.min(upper, cap);
      }
    }
    return new Zone(lower, upper, reference.basis(), reference.dates(), null);
  }

  private static Condition condition(String label, String observed, String required, Boolean met) {
    return new Condition(label, observed, required, met);
  }

  private static String title(String code) {
    return switch (code) {
      case "OPEN_PULLBACK" -> "回踩企稳建仓";
      case "OPEN_BREAKOUT" -> "放量突破建仓";
      case "ADD_RETEST" -> "突破确认后回踩加仓";
      default -> "减仓与风险退出";
    };
  }

  private static String money(Double value) {
    return value == null ? "缺失" : value(value) + "元";
  }

  private static String value(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static String percent(double value) {
    return value(value * 100) + "%";
  }

  /**
   * 一类新建或加仓方案的候选价带、参考证据及专属触发条件。
   *
   * @author chenxuefeng
   */
  private record Draft(
      /** 稳定策略代码。 */
      String code,
      /** 策略名称。 */
      String title,
      /** 候选观察价格带，元。 */
      Zone band,
      /** 形成价格带的原结构证据。 */
      Zone reference,
      /** 此方案专属的全部行情条件。 */
      List<Condition> conditions) {}
}
