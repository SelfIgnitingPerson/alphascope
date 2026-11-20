package com.alphascope.intelligence.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.intelligence.calculator.ForwardWindowCalculator;
import com.alphascope.intelligence.dto.DailyObservationEvidence;
import com.alphascope.intelligence.dto.ForwardInput;
import com.alphascope.intelligence.dto.ForwardQuery;
import com.alphascope.intelligence.dto.ForwardResultQuery;
import com.alphascope.intelligence.dto.ObservationQuery;
import com.alphascope.intelligence.entity.ForwardResult;
import com.alphascope.intelligence.entity.ResearchObservation;
import com.alphascope.intelligence.mapper.ForwardResultMapper;
import com.alphascope.intelligence.mapper.ResearchObservationMapper;
import com.alphascope.intelligence.service.ForwardValidationService;
import com.alphascope.intelligence.vo.ForwardEvidenceView;
import com.alphascope.intelligence.vo.ForwardOutcome;
import com.alphascope.intelligence.vo.ForwardValidationView;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.ObservationCaptureView;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.planning.dto.PlanEvidence;
import com.alphascope.planning.entity.ResearchPlan;
import com.alphascope.planning.mapper.ResearchPlanMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 实际观察与未来窗口分离，首次完成结果不覆盖；累计范围、版本与排除原因可追溯。
 *
 * @author chenxuefeng
 */
@Service
public class ForwardValidationServiceImpl implements ForwardValidationService {
  private final ResearchObservationMapper observations;
  private final ForwardResultMapper results;
  private final MarketService market;
  private final SourceLicenseService licenses;
  private final CapacityMapper capacity;
  private final ResearchPlanMapper plans;
  private final Clock clock;
  private final TransactionTemplate tx;

  public ForwardValidationServiceImpl(
      ResearchObservationMapper observations,
      ForwardResultMapper results,
      MarketService market,
      SourceLicenseService licenses,
      CapacityMapper capacity,
      ResearchPlanMapper plans,
      Clock clock,
      PlatformTransactionManager manager) {
    this.observations = observations;
    this.results = results;
    this.market = market;
    this.licenses = licenses;
    this.capacity = capacity;
    this.plans = plans;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  private ObservationQuery query(LocalDate date) {
    var now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    if (date == null || date.getYear() < 1990 || date.isAfter(now.toLocalDate()))
      throw new IllegalArgumentException("前瞻统计截止日不得为未来");
    return new ObservationQuery(
        null, date, date.equals(now.toLocalDate()) ? now : date.atTime(LocalTime.MAX));
  }

  private List<ResearchObservation> originals(ObservationQuery query) {
    var selected = new LinkedHashMap<String, ResearchObservation>();
    ObservationQuery page = query;
    List<ResearchObservation> rows;
    do {
      rows = observations.forward(page);
      for (var row : rows) {
        if (!Jsons.hash(row.getEvidenceJson()).equals(row.getEvidenceHash()))
          throw new IllegalStateException("每日判断指纹不一致");
        if (row.getCreatedAt() == null
            || !row.getBusinessDate().equals(row.getCreatedAt().toLocalDate())
            || !"LIVE".equals(row.getCaptureKind())) continue;
        var data = Jsons.read(row.getEvidenceJson(), DailyObservationEvidence.class);
        if (data.protocol() == null || !row.getBusinessDate().equals(data.research().quoteDate()))
          continue;
        selected.putIfAbsent(
            row.getSymbol()
                + "|"
                + row.getBusinessDate()
                + "|"
                + data.research().version()
                + "|"
                + data.protocol().version(),
            row);
      }
      if (rows.size() == 500) {
        page =
            new ObservationQuery(
                query.symbol(),
                query.date(),
                query.cutoff(),
                query.createdAfter(),
                rows.getLast().getId());
      }
    } while (rows.size() == 500);
    return selected.values().stream()
        .sorted(
            Comparator.comparing(ResearchObservation::getBusinessDate)
                .thenComparing(ResearchObservation::getId))
        .toList();
  }

  private List<ForwardResult> summaries(LocalDateTime cutoff, List<ResearchObservation> originals) {
    var rows = new ArrayList<ForwardResult>();
    for (int offset = 0; offset < originals.size(); offset += 500) {
      var ids =
          originals.subList(offset, Math.min(offset + 500, originals.size())).stream()
              .map(ResearchObservation::getId)
              .toList();
      rows.addAll(results.summaries(new ForwardResultQuery(cutoff, ids)));
    }
    return rows;
  }

  private LocalDate completedThrough(ObservationQuery query) {
    return query.cutoff().toLocalTime().isBefore(LocalTime.of(15, 30))
        ? query.date().minusDays(1)
        : query.date();
  }

  /** {@inheritDoc} */
  @Override
  public ObservationCaptureView evaluate(LocalDate date) {
    var query = query(date);
    var completionDate = completedThrough(query);
    var originals = originals(query);
    int saved = 0, reused = 0;
    var issues = new ArrayList<String>();
    var cache = new HashMap<String, List<MarketBarDO>>();
    var dateCache = new HashMap<LocalDate, List<LocalDate>>();
    var existing = new HashSet<String>();
    for (var result : summaries(query.cutoff(), originals)) {
      if (!Jsons.hash(result.getMetricsJson()).equals(result.getMetricsHash()))
        throw new IllegalStateException("前瞻结果指纹不一致");
      var prior = Jsons.read(result.getMetricsJson(), ForwardOutcome.class);
      if (Set.of("CALCULATED", "UNFILLED").contains(prior.window().status()))
        existing.add(
            result.getObservationId()
                + "|"
                + result.getActionCode()
                + "|"
                + result.getWindowDays());
    }
    for (var row : originals) {
      if (!row.getBusinessDate().isBefore(completionDate)) continue;
      var data = Jsons.read(row.getEvidenceJson(), DailyObservationEvidence.class);
      var research = data.research();
      for (var horizon : research.horizons())
        for (var action : horizon.actions())
          if (eligible(action))
            for (int window : List.of(5, 10, 20))
              try {
                if (existing.contains(
                    row.getId() + "|" + horizon.code() + ":" + action.code() + "|" + window)) {
                  reused++;
                  continue;
                }
                var days =
                    dateCache.computeIfAbsent(
                        row.getBusinessDate(),
                        signalDate ->
                            market
                                .expectedDates(
                                    TradingDatesQuery.builder()
                                        .symbol("000300.SH")
                                        .start(signalDate.plusDays(1))
                                        .end(completionDate)
                                        .build())
                                .stream()
                                .sorted()
                                .limit(20)
                                .toList());
                if (days.size() < window) continue;
                String key = row.getSymbol() + "|" + row.getBusinessDate();
                var bars =
                    cache.computeIfAbsent(
                        key,
                        k ->
                            market.bars(
                                row.getSymbol(),
                                DateRangeRequest.builder()
                                    .start(row.getBusinessDate())
                                    .end(days.getLast())
                                    .build()));
                var benchmark =
                    cache.computeIfAbsent(
                        "BENCH|" + row.getBusinessDate(),
                        k ->
                            market.bars(
                                "000300.SH",
                                DateRangeRequest.builder()
                                    .start(row.getBusinessDate())
                                    .end(days.getLast())
                                    .build()));
                var input =
                    new ForwardInput(
                        row.getEvidenceHash(),
                        row.getSymbol(),
                        row.getBusinessDate(),
                        research.source(),
                        research.close(),
                        research.quoteFactor(),
                        window,
                        data.protocol(),
                        days.subList(0, window),
                        bars.stream()
                            .filter(v -> !v.getTradeDate().isAfter(days.get(window - 1)))
                            .toList(),
                        benchmark.stream()
                            .filter(v -> !v.getTradeDate().isAfter(days.get(window - 1)))
                            .toList(),
                        market.calendarEvidence(
                            DateRangeRequest.builder()
                                .start(row.getBusinessDate().plusDays(1))
                                .end(days.get(window - 1))
                                .build()));
                var result = ForwardWindowCalculator.calculate(input);
                if (!Set.of("CALCULATED", "UNFILLED").contains(result.status())) {
                  if (issues.size() < 30)
                    issues.add(row.getSymbol() + " " + window + "日：" + result.reason());
                }
                licenses.requireEvidence(input, "DERIVED_REPORT");
                var frozen = new ForwardResult();
                frozen.setObservationId(row.getId());
                frozen.setActionCode(horizon.code() + ":" + action.code());
                frozen.setWindowDays(window);
                frozen.setInputJson(Jsons.write(input));
                frozen.setMetricsJson(
                    Jsons.write(
                        new ForwardOutcome(
                            row.getId(),
                            row.getSymbol(),
                            research.version(),
                            research.source(),
                            horizon.code(),
                            action.code(),
                            row.getCreatedAt(),
                            data.protocol().version(),
                            result)));
                frozen.setMetricsHash(Jsons.hash(frozen.getMetricsJson()));
                frozen.setContentHash(
                    Jsons.hash(frozen.getInputJson() + "\n" + frozen.getMetricsJson()));
                int inserted =
                    tx.execute(
                        status -> {
                          capacity.lockUser(TenantContext.id());
                          if (results.count() >= 30000)
                            throw new IllegalStateException("前瞻结果已达30000条容量上限，原结果保留");
                          return results.save(frozen);
                        });
                if (inserted > 0) saved++;
                else reused++;
              } catch (com.alphascope.common.task.TaskStoppedException stopped) {
                throw stopped;
              } catch (RuntimeException unavailable) {
                var reference = UUID.randomUUID().toString().substring(0, 8);
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn(
                        "前瞻评估失败 reference={} observation={}", reference, row.getId(), unavailable);
                if (issues.size() < 30)
                  issues.add(row.getSymbol() + " " + window + "日评估失败，维护编号" + reference);
              }
    }
    return new ObservationCaptureView(date, saved, reused, List.copyOf(issues));
  }

  /** {@inheritDoc} */
  @Override
  public ForwardValidationView summary(ForwardQuery scope) {
    var query = query(scope.asOf());
    ResearchPlan plan = null;
    PlanEvidence planEvidence = null;
    if (scope.planId() != null) {
      plan = plans.get(scope.planId());
      if (plan == null) throw new NotFoundException("计划不存在");
      if (!Jsons.hash(plan.getSnapshotJson()).equals(plan.getSnapshotHash()))
        throw new IllegalStateException("计划指纹不一致");
      planEvidence = Jsons.read(plan.getSnapshotJson(), PlanEvidence.class);
    }
    String symbol = plan == null ? scope.symbol() : plan.getSymbol();
    var originals =
        plan != null && scope.symbol() != null && !scope.symbol().equals(symbol)
            ? List.<ResearchObservation>of()
            : originals(
                new ObservationQuery(
                    symbol,
                    query.date(),
                    query.cutoff(),
                    plan == null ? null : plan.getCreatedAt(),
                    null));
    var sources = new TreeSet<String>();
    var frozen = new HashMap<String, ForwardOutcome>();
    for (var row : summaries(query.cutoff(), originals)) {
      if (!Jsons.hash(row.getMetricsJson()).equals(row.getMetricsHash()))
        throw new IllegalStateException("前瞻结果指纹不一致");
      var outcome = Jsons.read(row.getMetricsJson(), ForwardOutcome.class);
      var key =
          outcome.observationId()
              + "|"
              + outcome.horizon()
              + ":"
              + outcome.actionCode()
              + "|"
              + row.getWindowDays();
      var previous = frozen.get(key);
      if (previous != null && Set.of("CALCULATED", "UNFILLED").contains(previous.window().status()))
        continue;
      frozen.put(
          outcome.observationId()
              + "|"
              + outcome.horizon()
              + ":"
              + outcome.actionCode()
              + "|"
              + row.getWindowDays(),
          outcome);
    }
    var groups = new LinkedHashMap<String, List<Candidate>>();
    var noEntryReasons = new TreeMap<String, Integer>();
    int observed = 0;
    int candidateObservations = 0;
    for (var row : originals) {
      var data = Jsons.read(row.getEvidenceJson(), DailyObservationEvidence.class);
      var research = data.research();
      if (plan != null && !Objects.equals(research.version(), planEvidence.research().version()))
        continue;
      observed++;
      if (research.inputSources() != null) sources.addAll(research.inputSources());
      var entryStatuses = new HashSet<String>();
      boolean candidateFound = false;
      for (var horizon : research.horizons()) {
        if (plan != null && !plan.getHorizon().equals(horizon.code())) continue;
        for (var action : horizon.actions()) {
          if (!entryAction(action) || plan != null && !plan.getActionCode().equals(action.code()))
            continue;
          entryStatuses.add(action.status());
          if (eligible(action)) {
            candidateFound = true;
            String key =
                research.version()
                    + "|"
                    + research.source()
                    + "|"
                    + horizon.code()
                    + "|"
                    + action.code()
                    + "|"
                    + data.protocol().version();
            groups
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(
                    new Candidate(
                        row, research, horizon.code(), action.code(), data.protocol().version()));
          }
        }
      }
      if (candidateFound) candidateObservations++;
      else noEntryReasons.merge(noEntryReason(entryStatuses), 1, Integer::sum);
    }
    var firstCandidate =
        groups.values().stream()
            .flatMap(List::stream)
            .map(c -> c.row().getBusinessDate())
            .min(LocalDate::compareTo)
            .orElse(null);
    var completedThrough = completedThrough(query);
    var tradingDays = confirmedTradingDays(firstCandidate, completedThrough);
    var answer = new ArrayList<ForwardValidationView.Group>();
    for (var group : groups.values()) {
      var first = group.getFirst();
      var windows = new ArrayList<ForwardValidationView.Statistics>();
      for (int window : List.of(5, 10, 20)) {
        var included = new ArrayList<ForwardOutcome>();
        var reserved = new HashMap<String, LocalDate>();
        var exclusions = new TreeMap<String, Integer>();
        int attempted = 0;
        for (var c : group) {
          var outcome =
              frozen.get(c.row().getId() + "|" + c.horizon() + ":" + c.action() + "|" + window);
          if (outcome == null) {
            exclusions.merge(
                pendingReason(c.row().getBusinessDate(), completedThrough, tradingDays, window),
                1,
                Integer::sum);
            continue;
          }
          var w = outcome.window();
          if (!Set.of("CALCULATED", "UNFILLED").contains(w.status())) {
            exclusions.merge("资料不足：" + w.reason(), 1, Integer::sum);
            continue;
          }
          if (reserved.containsKey(c.row().getSymbol())
              && !w.entryDate().isAfter(reserved.get(c.row().getSymbol()))) {
            exclusions.merge("OVERLAPPING_SIGNAL", 1, Integer::sum);
            continue;
          }
          reserved.put(c.row().getSymbol(), w.exitDate());
          attempted++;
          if ("UNFILLED".equals(w.status())) {
            exclusions.merge("未成交：" + w.reason(), 1, Integer::sum);
            continue;
          }
          included.add(outcome);
          sources.addAll(w.inputSources());
        }
        var returns = included.stream().mapToDouble(x -> x.window().netReturn()).toArray();
        int count = returns.length;
        Double mean = count == 0 ? null : Arrays.stream(returns).average().orElseThrow();
        Double wins =
            count == 0 ? null : (double) Arrays.stream(returns).filter(v -> v > 0).count() / count;
        var gains = Arrays.stream(returns).filter(v -> v > 0).average();
        var losses = Arrays.stream(returns).filter(v -> v < 0).map(Math::abs).average();
        Double ratio =
            gains.isPresent() && losses.isPresent()
                ? gains.getAsDouble() / losses.getAsDouble()
                : null;
        var matched = included.stream().filter(x -> x.window().benchmarkReturn() != null).toList();
        Double excess =
            matched.isEmpty()
                ? null
                : matched.stream()
                    .mapToDouble(x -> x.window().netReturn() - x.window().benchmarkReturn())
                    .average()
                    .orElseThrow();
        Double drawdown =
            count == 0
                ? null
                : included.stream().mapToDouble(x -> x.window().maxDrawdown()).max().orElseThrow();
        windows.add(
            new ForwardValidationView.Statistics(
                window,
                count < 30 ? "COLLECTING" : "DESCRIPTIVE_ONLY",
                count,
                attempted,
                mean,
                wins,
                ratio,
                drawdown,
                excess,
                matched.size(),
                Map.copyOf(exclusions)));
      }
      answer.add(
          new ForwardValidationView.Group(
              first.research().version(),
              first.research().source(),
              first.horizon(),
              first.action(),
              first.protocol(),
              List.copyOf(windows)));
    }
    var limitations =
        List.of(
            "只纳入实际当日形成且明确满足建仓或加仓条件的冻结判断；事后回看、等待和风险拦截不产生交易样本。",
            "按方法、来源、周期、行动与执行协议分组，同股票重叠窗口排除；不是当前自选的历史回填。",
            "每个窗口独立名义账户100000元，次日开盘尝试、固定窗口末收盘退出；扣双边佣金、最低5元、卖出印花税及滑点。不是个人仓位建议。",
            "单价日、跳空达到4.5%、参与额超1%保守拒绝；缺失停牌行情或公司行动账务不能假定成交。日线不能证明逐笔可成交性。",
            "回撤为单个样本日收盘净权益最大回撤，不能解释为组合连续净值回撤；相对指数收益差中仅策略扣费。",
            "30例只是展示小样本边界，不是有效性认证。历史判断按已选证券或原计划范围统计。");
    return new ForwardValidationView(
        scope.asOf(),
        observed,
        candidateObservations,
        Map.copyOf(noEntryReasons),
        false,
        List.copyOf(answer),
        limitations,
        Set.copyOf(sources));
  }

  private static String noEntryReason(Set<String> statuses) {
    if (statuses.isEmpty()) return "NO_ENTRY_ACTION";
    if (statuses.contains("RISK_BLOCKED")) return "RISK_BLOCKED";
    if (statuses.contains("WAIT") || statuses.contains("WATCH")) return "WAITING_FOR_CONDITIONS";
    return "ENTRY_EVIDENCE_INSUFFICIENT";
  }

  private List<LocalDate> confirmedTradingDays(LocalDate firstSignal, LocalDate completedThrough) {
    if (firstSignal == null || !firstSignal.isBefore(completedThrough)) return List.of();
    try {
      return market
          .expectedDates(
              TradingDatesQuery.builder()
                  .symbol("000300.SH")
                  .start(firstSignal.plusDays(1))
                  .end(completedThrough)
                  .build())
          .stream()
          .sorted()
          .toList();
    } catch (IllegalStateException | IllegalArgumentException unavailable) {
      return null;
    }
  }

  private String pendingReason(
      LocalDate signal, LocalDate completedThrough, List<LocalDate> tradingDays, int window) {
    if (!signal.isBefore(completedThrough)) return "WINDOW_NOT_MATURE";
    if (tradingDays == null) return "CALENDAR_UNAVAILABLE";
    int found = Collections.binarySearch(tradingDays, signal);
    int firstDay = found >= 0 ? found + 1 : -found - 1;
    return tradingDays.size() - firstDay < window ? "WINDOW_NOT_MATURE" : "AWAITING_EVALUATION";
  }

  /** {@inheritDoc} */
  @Override
  public ForwardEvidenceView evidence(long id) {
    var row = results.get(id);
    if (row == null) throw new NotFoundException("前瞻结果不存在");
    if (!Jsons.hash(row.getInputJson() + "\n" + row.getMetricsJson()).equals(row.getContentHash())
        || !Jsons.hash(row.getMetricsJson()).equals(row.getMetricsHash()))
      throw new IllegalStateException("前瞻冻结证据指纹不匹配");
    licenses.requireEvidence(row, "EXPORT");
    return new ForwardEvidenceView(
        row.getId(),
        row.getCreatedAt(),
        Jsons.read(row.getInputJson(), ForwardInput.class),
        Jsons.read(row.getMetricsJson(), ForwardOutcome.class),
        row.getContentHash());
  }

  private static boolean eligible(HoldingResearchView.Action action) {
    return entryAction(action) && "CONDITIONAL_MATCH".equals(action.status());
  }

  private static boolean entryAction(HoldingResearchView.Action action) {
    return "ALL".equals(action.conditionLogic())
        && (action.code().startsWith("OPEN_")
            || action.code().startsWith("ADD_")
            || action.code().endsWith("_OPEN")
            || action.code().endsWith("_ADD"));
  }

  /**
   * 已冻结且明确满足入场条件的窗口身份，统计时保留原版本。
   *
   * @author chenxuefeng
   */
  private record Candidate(
      ResearchObservation row,
      HoldingResearchView research,
      String horizon,
      String action,
      String protocol) {}
}
