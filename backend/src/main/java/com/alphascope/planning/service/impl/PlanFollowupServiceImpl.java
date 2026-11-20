package com.alphascope.planning.service.impl;

import com.alphascope.common.response.PageResponse;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.planning.dto.PlanQuery;
import com.alphascope.planning.dto.PlanReviewInput;
import com.alphascope.planning.service.PlanFollowupService;
import com.alphascope.planning.service.ResearchPlanService;
import com.alphascope.planning.vo.PlanFollowupView;
import com.alphascope.planning.vo.PlanSummary;
import com.alphascope.planning.vo.PlanTodoView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 各计划使用独立事务追加证据，单项缺项保留待办，不拖垮收盘报告。
 *
 * @author chenxuefeng
 */
@Service
public class PlanFollowupServiceImpl implements PlanFollowupService {
  private final ResearchPlanService plans;

  public PlanFollowupServiceImpl(ResearchPlanService plans) {
    this.plans = plans;
  }

  /** {@inheritDoc} */
  @Override
  public PlanTodoView todos(LocalDate date) {
    var all = activePlansAt(date, plans::page);
    Predicate<PlanSummary> unread = p -> !p.acknowledged();
    int risk = (int) all.stream().filter(this::unreadOriginalRisk).count();
    int changed =
        (int)
            all.stream()
                .filter(unread)
                .filter(
                    p ->
                        Set.of("CONDITIONS_CHANGED", "METHOD_CHANGED", "SOURCE_CHANGED")
                            .contains(Objects.toString(p.latestReviewStatus(), "")))
                .count();
    int data =
        (int)
            all.stream()
                .filter(unread)
                .filter(p -> "DATA_INSUFFICIENT".equals(p.latestReviewStatus()))
                .count();
    int due = (int) all.stream().filter(p -> !Objects.equals(p.latestReviewDate(), date)).count();
    int reviewed =
        (int)
            all.stream()
                .filter(
                    p ->
                        Objects.equals(p.latestReviewDate(), date)
                            && Set.of(
                                    "UNCHANGED",
                                    "CONDITIONS_CHANGED",
                                    "ORIGINAL_STOP_BREACHED",
                                    "METHOD_CHANGED",
                                    "SOURCE_CHANGED")
                                .contains(Objects.toString(p.latestReviewStatus(), "")))
                .count();
    int acknowledged = (int) all.stream().filter(p -> p.acknowledged()).count();
    var items =
        all.stream()
            .filter(p -> !p.acknowledged() || !Objects.equals(p.latestReviewDate(), date))
            .sorted(
                Comparator.comparingInt(
                        (PlanSummary p) ->
                            switch (Objects.toString(p.latestReviewStatus(), "")) {
                              case "ORIGINAL_STOP_BREACHED" -> p.acknowledged() ? 4 : 0;
                              case "CONDITIONS_CHANGED", "METHOD_CHANGED", "SOURCE_CHANGED" ->
                                  p.acknowledged() ? 4 : 1;
                              case "DATA_INSUFFICIENT" -> p.acknowledged() ? 4 : 2;
                              default -> 3;
                            })
                    .thenComparingLong(p -> p.id()))
            .limit(5)
            .toList();
    return new PlanTodoView(
        date,
        all.size(),
        reviewed,
        risk,
        changed,
        data,
        due,
        acknowledged,
        items,
        all.stream()
            .flatMap(p -> p.inputSources() == null ? Stream.empty() : p.inputSources().stream())
            .collect(Collectors.toSet()));
  }

  /** {@inheritDoc} */
  @Override
  public java.util.List<PlanSummary> originalRiskAlerts(LocalDate date) {
    return activePlansAt(date, plans::pageWithOriginalRisk).stream()
        .filter(p -> "ORIGINAL_STOP_BREACHED".equals(p.latestReviewStatus()))
        .sorted(Comparator.comparing(PlanSummary::acknowledged).thenComparingLong(PlanSummary::id))
        .toList();
  }

  private boolean unreadOriginalRisk(PlanSummary plan) {
    return !plan.acknowledged() && "ORIGINAL_STOP_BREACHED".equals(plan.latestReviewStatus());
  }

  private List<PlanSummary> activePlansAt(
      LocalDate date, Function<PlanQuery, PageResponse<PlanSummary>> loadPage) {
    var all = new ArrayList<PlanSummary>();
    var query = new PlanQuery();
    query.setAsOf(date);
    query.setArchived(false);
    query.setSize(100);
    for (int page = 1; page <= 10; page++) {
      query.setPage(page);
      var result = loadPage.apply(query);
      all.addAll(result.items());
      if ((long) page * query.getSize() >= result.total()) break;
    }
    return all;
  }

  /** {@inheritDoc} */
  @Override
  public PlanFollowupView review(LocalDate date) {
    return reviewBatch(date, 100);
  }

  /** {@inheritDoc} */
  @Override
  public PlanFollowupView reviewAll(LocalDate date) {
    return reviewBatch(date, 1000);
  }

  /** HTTP批量有界；后台逐项覆盖账号保护上限内的全部计划。 */
  private PlanFollowupView reviewBatch(LocalDate date, int limit) {
    var query = new PlanQuery();
    query.setAsOf(date);
    query.setArchived(false);
    query.setSize(100);
    int reviewed = 0, remaining = 0, attempted = 0;
    var issues = new ArrayList<String>();
    for (int page = 1; page <= 10; page++) {
      query.setPage(page);
      var result = plans.page(query);
      for (var plan : result.items()) {
        ExecutionContext.checkpoint();
        if (Objects.equals(date, plan.latestReviewDate())
            && !"DATA_INSUFFICIENT".equals(plan.latestReviewStatus())) continue;
        if (attempted++ >= limit) {
          remaining++;
          continue;
        }
        try {
          var value = plans.review(plan.id(), new PlanReviewInput(date));
          reviewed++;
          if ("DATA_INSUFFICIENT".equals(value.status())) {
            remaining++;
            issues.add(plan.symbol() + "：" + value.explanation());
          }
        } catch (com.alphascope.common.task.TaskStoppedException stopped) {
          throw stopped;
        } catch (RuntimeException failure) {
          remaining++;
          var reference = java.util.UUID.randomUUID().toString().substring(0, 8);
          LoggerFactory.getLogger(getClass())
              .warn("计划复核失败 plan={} reference={}", plan.id(), reference, failure);
          issues.add(plan.symbol() + "：复核未完成，请打开计划核对资料；维护编号" + reference);
        }
      }
      if ((long) page * query.getSize() >= result.total()) break;
    }
    return new PlanFollowupView(date, reviewed, remaining, List.copyOf(issues));
  }
}
