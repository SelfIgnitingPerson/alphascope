package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.ResearchPriority;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 统一首页与个股的事实优先级；弱走势、已确认组合和结构风险分开，不新增交易阈值。
 *
 * @author chenxuefeng
 */
public final class ResearchPriorityCalculator {
  private ResearchPriorityCalculator() {}

  /**
   * 从已有方案生成有事实依据的观察事项，按核对顺序排列。
   *
   * @param view 当日同源研究，不读取外部数据
   * @return 非空列表；不足时返回明确资料问题
   */
  public static List<ResearchPriority> observations(HoldingResearchView view) {
    boolean demo = view.priceVolume() != null && "DEMO".equals(view.priceVolume().status());
    if (demo
        || view.close() == null
        || !Double.isFinite(view.close())
        || view.close() <= 0
        || !view.asOf().equals(view.quoteDate())
        || view.source() == null
        || view.source().isBlank()) {
      return List.of(
          ResearchPriority.builder()
              .code("DATA_GAP")
              .level("DATA")
              .title(demo ? "演示资料，不形成真实研究判断" : "先核对所选日的有效行情")
              .explanation(
                  demo
                      ? "当前资料仅用于演示界面。"
                      : "研究日"
                          + view.asOf()
                          + "；已存行情日"
                          + (view.quoteDate() == null ? "暂无" : view.quoteDate())
                          + "。行情或来源不足，不能用旧价格作本期买卖判断，风险也不能据此视为解除。")
              .facts(
                  List.of(
                      "研究日：" + view.asOf(),
                      "已存行情日：" + (view.quoteDate() == null ? "暂无" : view.quoteDate())))
              .nextChecks(List.of("核对研究日期、行情来源和任务中心的具体缺项。", "补齐有效资料后再复核方案，不用0代替缺失值。"))
              .build());
    }
    var results = new ArrayList<ResearchPriority>();
    for (var h : view.horizons()) {
      if ("UNAVAILABLE".equals(h.status())) {
        results.add(
            ResearchPriority.builder()
                .code("DATA_GAP")
                .level("DATA")
                .title(h.title() + "资料不足")
                .explanation(h.conclusion())
                .horizon(h.code())
                .facts(h.missing() == null ? List.of() : h.missing().stream().limit(3).toList())
                .nextChecks(List.of("按具体缺项补齐资料后，再评估该周期；不能由没有风险提示反推可以买入。"))
                .build());
        continue;
      }
      for (var a : h.actions()) {
        if ("ANY".equals(a.conditionLogic())
            && List.of("WATCH", "CONDITIONAL_MATCH").contains(a.status())) {
          var confirmed =
              a.conditions() == null
                  ? List.<com.alphascope.intelligence.vo.PositionStrategy.Condition>of()
                  : a.conditions().stream().filter(c -> Boolean.TRUE.equals(c.met())).toList();
          boolean structure =
              a.stopReference() != null
                  && a.stopReference() > 0
                  && view.close() <= a.stopReference();
          boolean business = confirmed.stream().anyMatch(c -> "经营风险".equals(c.label()));
          String code =
              structure
                  ? "STRUCTURE_RISK"
                  : business
                      ? "BUSINESS_RISK"
                      : "CONDITIONAL_MATCH".equals(a.status()) ? "CONFIRMED_RISK" : "TREND_WATCH";
          String title =
              structure
                  ? "收盘已触及本期结构风险参考"
                  : business
                      ? "经营风险需要优先核实"
                      : !confirmed.isEmpty()
                          ? confirmed.getFirst().label()
                          : "CONDITIONAL_MATCH".equals(a.status())
                              ? "风险组合已有触发记录，需核对明细"
                              : "走势偏弱，减仓组合尚未确认";
          var facts = new ArrayList<String>();
          if (structure)
            facts.add("本期收盘" + money(view.close()) + "元；本期结构参考" + money(a.stopReference()) + "元");
          for (var c : confirmed)
            if (!structure || !"收盘触及结构止损".equals(c.label()))
              facts.add(c.label() + "：" + c.observed());
          if (facts.isEmpty() && view.priceVolume() != null) {
            var p = view.priceVolume();
            if (p.ma20() != null)
              facts.add("收盘" + money(view.close()) + "元；MA20为" + money(p.ma20()) + "元");
            if (p.return5() != null) facts.add("近5期收益" + money(p.return5() * 100) + "%");
            if (p.volumeRatio5() != null)
              facts.add("5日成交额量比" + money(p.volumeRatio5()) + "倍，量能组合仍需逐项核对");
          }
          if (h.evidence() != null && facts.size() < 3)
            facts.addAll(h.evidence().stream().limit(3 - facts.size()).toList());
          var next = new ArrayList<String>();
          if (structure) next.add("先按已保存计划的原风险线复核；本期新参考不能替换或下移原计划边界。");
          else if (business) next.add("核对同口径财报、现金流和已触发规则的具体证据，确认经营问题是否持续。");
          else if ("TREND_WATCH".equals(code)) next.add("先核对趋势与量能能否改善；观察信号不等于减仓组合已经确认，也不是买入确认。");
          if (a.invalidation() != null) next.addAll(a.invalidation());
          if (next.isEmpty()) next.add("下一有效研究日逐项核对已触发条件是否变化，再对照原计划评估。");
          results.add(
              ResearchPriority.builder()
                  .code(code)
                  .level(structure ? "FIRST" : "TREND_WATCH".equals(code) ? "WATCH" : "IMPORTANT")
                  .title(title)
                  .explanation(a.conclusion())
                  .horizon(h.code())
                  .actionCode(a.code())
                  .facts(facts.stream().distinct().limit(3).toList())
                  .nextChecks(next.stream().distinct().limit(2).toList())
                  .build());
        } else if ("ALL".equals(a.conditionLogic()) && "CONDITIONAL_MATCH".equals(a.status())) {
          var facts =
              a.conditions() == null
                  ? List.<String>of()
                  : a.conditions().stream()
                      .filter(c -> Boolean.TRUE.equals(c.met()))
                      .map(c -> c.label() + "：" + c.observed())
                      .limit(3)
                      .toList();
          results.add(
              ResearchPriority.builder()
                  .code("ENTRY_MATCH")
                  .level("WATCH")
                  .title(a.title() + "的观察条件满足")
                  .explanation("行情条件成立仍需核对失效条件和个人前提，不能视作已验证盈利或可直接成交。")
                  .horizon(h.code())
                  .actionCode(a.code())
                  .facts(facts)
                  .nextChecks(
                      a.invalidation() == null || a.invalidation().isEmpty()
                          ? List.of("对照原方案核对价格、量能与失效前提，再作人工确认。")
                          : a.invalidation().stream().limit(2).toList())
                  .build());
        }
      }
    }
    if (results.isEmpty()) {
      var h = view.horizons().stream().findFirst().orElse(null);
      results.add(
          ResearchPriority.builder()
              .code("WAIT")
              .level("WATCH")
              .title("尚无买入确认，继续核对观察条件")
              .explanation(h == null ? "尚无可用周期方案。" : h.conclusion())
              .horizon(h == null ? null : h.code())
              .facts(List.of("本期收盘" + money(view.close()) + "元；等待条件不等于股票没有风险。"))
              .nextChecks(List.of("在下一有效研究日核对未满足条件与原计划的失效边界。"))
              .build());
    }
    results.sort(Comparator.comparingInt(ResearchPriorityCalculator::order));
    return List.copyOf(results);
  }

  /**
   * 仅用于事实核对顺序，不向用户显示为量化风险分数。
   *
   * @param priority 待排列事项
   * @return 越小越优先
   */
  public static int order(ResearchPriority priority) {
    return switch (priority.code()) {
      case "STRUCTURE_RISK" -> 0;
      case "BUSINESS_RISK" -> 10;
      case "CONFIRMED_RISK" -> 20;
      case "TREND_WATCH" -> 30;
      case "ENTRY_MATCH" -> 40;
      case "DATA_GAP" -> 50;
      default -> 60;
    };
  }

  private static String money(Double value) {
    return value == null || !Double.isFinite(value)
        ? "待核对"
        : java.math.BigDecimal.valueOf(value)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString();
  }
}
