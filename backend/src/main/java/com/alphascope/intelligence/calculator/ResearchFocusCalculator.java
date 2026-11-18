package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.dto.ResearchFocusInput;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.intelligence.vo.ResearchPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
      if (comparable && old.stream().noneMatch(p -> sameIdentity(p, key))) change = "较上期新增或改变的风险信号";
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
        .changeLabel(change)
        .priorities(List.copyOf(ordered))
        .build();
  }

  /**
   * 当前页按事实轻重排序；不是全量跨页排名或风险概率。
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
