package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.dto.ResearchFocusInput;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.intelligence.vo.ResearchPriority;
import com.alphascope.operations.policy.SourceEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 以同一份事实优先级解释两期变化；弱观察、已确认风险和新条件分别表达。
 *
 * @author chenxuefeng
 */
public final class ResearchFocusCalculator {
  private ResearchFocusCalculator() {}

  /**
   * 比较当前保存版本；缺少同源同方法上期时，不标记“新增”或“解除”。
   *
   * @param input 当前研究、上期研究及核验的上期日期
   * @return 一只证券最应先核对的事项及其他周期线索
   */
  public static ResearchFocusView.Item compare(ResearchFocusInput input) {
    var current = input.current();
    var previous = input.previous();
    var previousDate = input.previousDate();
    var now = observations(current);
    var selected = now.getFirst();
    boolean comparable =
        previous != null
            && previousDate != null
            && previousDate.isBefore(current.asOf())
            && Objects.equals(current.symbol(), previous.symbol())
            && current.version() != null
            && previous.version() != null
            && current.close() != null
            && previousDate.equals(previous.asOf())
            && previousDate.equals(previous.quoteDate())
            && previous.close() != null
            && current.asOf().equals(current.quoteDate())
            && Objects.equals(current.version(), previous.version())
            && current.source() != null
            && current.source().equals(previous.source());
    var old = comparable ? observations(previous) : List.<ResearchPriority>of();
    String category = category(selected);
    boolean fresh = false;
    String change = comparable ? "上期也有此信号" : "缺少可比上期，不标记新增或解除";
    if ("RISK".equals(category)) {
      var key = selected;
      if (comparable
          && completeHorizon(previous, key.horizon())
          && old.stream().noneMatch(p -> sameIdentity(p, key))) change = "较上期新增或改变的风险信号";
    } else if ("MATCH".equals(category)) {
      var newly =
          now.stream()
              .filter(p -> "ENTRY_MATCH".equals(p.code()))
              .filter(
                  p ->
                      comparable
                          && previous.horizons().stream()
                              .filter(
                                  h ->
                                      Objects.equals(h.code(), p.horizon())
                                          && !"UNAVAILABLE".equals(h.status()))
                              .flatMap(h -> h.actions().stream())
                              .anyMatch(
                                  a ->
                                      Objects.equals(a.code(), p.actionCode())
                                          && !"UNAVAILABLE".equals(a.status())
                                          && !"CONDITIONAL_MATCH".equals(a.status())))
              .findFirst();
      if (newly.isPresent()) {
        selected = newly.get();
        fresh = true;
        change = "较上期新满足，仍待人工复核";
      } else if (comparable) change = "需核对上期；不重复当作新机会";
    } else if ("WAIT".equals(category) && comparable) {
      boolean changed =
          current.horizons().stream()
              .anyMatch(
                  h ->
                      previous.horizons().stream()
                          .anyMatch(
                              p ->
                                  Objects.equals(h.code(), p.code())
                                      && !Objects.equals(h.status(), p.status())));
      if (changed) {
        category = "CHANGED";
        change = "周期判断发生变化，尚无买入确认";
      } else change = "暂无新的买入确认或退出组合";
    } else if ("DATA_GAP".equals(category)) change = "资料不足，不据此判断风险解除";
    String changeState = "UNCOMPARABLE";
    if (previous != null
        && current.source() != null
        && previous.source() != null
        && !Objects.equals(current.source(), previous.source())) changeState = "SOURCE_CHANGED";
    else if (previous != null
        && current.version() != null
        && previous.version() != null
        && !Objects.equals(current.version(), previous.version())) changeState = "METHOD_CHANGED";
    else if ("DATA_GAP".equals(category)) changeState = "DATA_GAP";
    else if (comparable) {
      boolean nowRisk =
          now.stream()
              .anyMatch(
                  p ->
                      Set.of("STRUCTURE_RISK", "BUSINESS_RISK", "CONFIRMED_RISK")
                          .contains(p.code()));
      boolean oldRisk =
          old.stream()
              .anyMatch(
                  p ->
                      Set.of("STRUCTURE_RISK", "BUSINESS_RISK", "CONFIRMED_RISK")
                          .contains(p.code()));
      boolean complete =
          old.stream()
              .filter(
                  p ->
                      Set.of("STRUCTURE_RISK", "BUSINESS_RISK", "CONFIRMED_RISK")
                          .contains(p.code()))
              .allMatch(p -> completeHorizon(current, p.horizon()));
      boolean newRisk =
          now.stream()
              .filter(
                  p ->
                      Set.of("STRUCTURE_RISK", "BUSINESS_RISK", "CONFIRMED_RISK")
                          .contains(p.code()))
              .anyMatch(
                  p ->
                      completeHorizon(previous, p.horizon())
                          && old.stream().noneMatch(o -> sameIdentity(o, p)));
      changeState =
          newRisk || fresh ? "NEW" : !nowRisk && oldRisk && complete ? "RECOVERED" : "ONGOING";
      if ("RECOVERED".equals(changeState)) change = "上期已确认风险本期未再触发；原计划历史触及记录仍保留，不据此确认可以买入";
    }
    if ("METHOD_CHANGED".equals(changeState)) change = "分析方法已变化，应分开复核规则变化与公司新事实";
    if ("SOURCE_CHANGED".equals(changeState)) change = "数据来源已变化，差异不直接视作公司新事实";
    var ordered = new ArrayList<ResearchPriority>();
    ordered.add(selected);
    for (var p : now) if (!sameIdentity(p, selected)) ordered.add(p);
    String title = fresh ? "观察条件新满足" : selected.title();
    return ResearchFocusView.Item.builder()
        .symbol(current.symbol())
        .name(current.name())
        .category(category)
        .title(title)
        .explanation(selected.explanation())
        .horizon(selected.horizon())
        .quoteDate(current.quoteDate())
        .comparisonDate(previousDate)
        .newlyMatched(fresh)
        .inputSources(
            SourceEvidence.inspect(previous == null ? List.of(current) : List.of(current, previous))
                .sources())
        .changeState(changeState)
        .changeLabel(change)
        .priorities(List.copyOf(ordered))
        .build();
  }

  /**
   * 按事实轻重给全量自选排序，排序后分页；数值不是风险概率。
   *
   * @param item 本页证券事项
   * @return 越小越优先
   */
  public static int priority(ResearchFocusView.Item item) {
    return item.priorities() == null || item.priorities().isEmpty()
        ? 50
        : ResearchPriorityCalculator.order(item.priorities().getFirst())
            - (item.newlyMatched() ? 1 : 0);
  }

  private static boolean completeHorizon(HoldingResearchView view, String code) {
    return view.horizons().stream()
        .anyMatch(
            h ->
                Objects.equals(code, h.code())
                    && !"UNAVAILABLE".equals(h.status())
                    && h.missing() != null
                    && h.missing().isEmpty());
  }

  private static List<ResearchPriority> observations(HoldingResearchView view) {
    return view.priorities() == null || view.priorities().isEmpty()
        ? ResearchPriorityCalculator.observations(view)
        : view.priorities();
  }

  private static String category(ResearchPriority priority) {
    return switch (priority.code()) {
      case "DATA_GAP" -> "DATA_GAP";
      case "ENTRY_MATCH" -> "MATCH";
      case "WAIT" -> "WAIT";
      default -> "RISK";
    };
  }

  private static boolean sameIdentity(ResearchPriority first, ResearchPriority second) {
    return Objects.equals(first.code(), second.code())
        && Objects.equals(first.horizon(), second.horizon())
        && Objects.equals(first.actionCode(), second.actionCode());
  }
}
