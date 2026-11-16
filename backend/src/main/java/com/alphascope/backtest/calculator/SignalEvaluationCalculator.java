package com.alphascope.backtest.calculator;

import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.vo.BacktestView;
import com.alphascope.backtest.vo.SignalStatisticView;
import com.alphascope.backtest.vo.ValidationWindow;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.JsonFingerprint;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.signal.entity.SignalEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 已保存信号的可审计历史验证：先判断成熟与可用时点，再统计非重叠窗口和同日基准。
 *
 * <p>严格时点模式拒绝缺少保存/修订时刻的证据，不能从当前版本推造历史可得性。
 *
 * @author chenxuefeng
 */
public final class SignalEvaluationCalculator {
  private SignalEvaluationCalculator() {}

  /**
   * 使用已保存输入计算描述统计及预先划定的较晚留出区间。
   *
   * @param input 日期、同源行情、基准、信号、日历和样本阈值
   * @return 明确成熟样本、排除原因、基准缺失及方法边界的结果
   */
  public static BacktestView evaluate(SignalEvaluationInput input) {
    SignalStatisticsRequest request = input.request();
    LocalDate evaluationDate =
        request.getEvaluationDate() == null ? request.getEnd() : request.getEvaluationDate();
    LocalDate split = splitDate(request);
    var statistics = range(input, request.getStart(), request.getEnd(), evaluationDate);
    List<ValidationWindow> windows =
        split == null
            ? List.of()
            : List.of(
                new ValidationWindow(
                    "EARLY",
                    request.getStart(),
                    split.minusDays(1),
                    range(input, request.getStart(), split.minusDays(1), split.minusDays(1))),
                new ValidationWindow(
                    "HOLDOUT",
                    split,
                    request.getEnd(),
                    range(input, split, request.getEnd(), evaluationDate)));
    int samples =
        statistics.values().stream()
            .flatMap(value -> value.values().stream())
            .mapToInt(SignalStatisticView::sampleCount)
            .sum();
    boolean triggered =
        input.signals().stream().anyMatch(event -> "TRIGGERED".equals(event.getStatus()));
    String state =
        samples > 0
            ? "OBSERVED"
            : input.signals().isEmpty()
                ? "INSUFFICIENT_DATA"
                : triggered ? "INSUFFICIENT_DATA" : "NOT_TRIGGERED";
    String reason =
        samples > 0
            ? null
            : input.signals().isEmpty()
                ? "查询区间尚无已保存规则记录"
                : triggered ? "没有满足成熟窗口、连续行情、版本及时点条件的收益样本，请查看各规则排除原因" : "已有规则记录均未明确触发，不能当作收益样本";
    return new BacktestView(
        state,
        reason,
        statistics,
        List.of(
            "收盘到收盘的研究描述统计，不能按信号日收盘价假定可成交；未扣成本与滑点",
            "每条规则和窗口分别排除重叠信号；不同规则之间仍可能相关，不视为独立试验",
            "沪深300仅与同日期且完整的基准窗口比较，缺失基准不按0收益计算",
            "较早区间的收益必须在留出区间开始前成熟；留出区间不用于事后选择阈值",
            "严格时点模式从不可变修订重建信号、原价和同源因子；未知入库时刻不补造，权威日历也核验保存时点",
            "小样本、幸存者偏差及来源修订可能影响结论；这不是完整策略回测"),
        mode(request),
        evaluationDate,
        split,
        windows,
        input.minimumSamples(),
        "SAVED_SIGNAL_VALIDATION_V3");
  }

  private static LocalDate splitDate(SignalStatisticsRequest request) {
    if (!request.getStart().isBefore(request.getEnd())) {
      return null;
    }
    LocalDate value = request.getSplitDate();
    if (value == null) {
      value =
          request
              .getStart()
              .plusDays(
                  Math.max(
                      1, ChronoUnit.DAYS.between(request.getStart(), request.getEnd()) * 7 / 10));
    }
    if (!value.isAfter(request.getStart()) || value.isAfter(request.getEnd())) {
      throw new IllegalArgumentException("留出区间首日须晚于开始日期且不晚于结束日期");
    }
    return value;
  }

  private static Map<String, Map<String, SignalStatisticView>> range(
      SignalEvaluationInput input, LocalDate start, LocalDate end, LocalDate cutoff) {
    Map<String, List<SignalEvent>> groups = new TreeMap<>();
    for (SignalEvent event : input.signals()) {
      if (!event.getTradeDate().isBefore(start) && !event.getTradeDate().isAfter(end)) {
        groups.computeIfAbsent(event.getRuleCode(), key -> new ArrayList<>()).add(event);
      }
    }
    Map<LocalDate, MarketBarDO> bars = index(input.bars());
    Map<LocalDate, MarketBarDO> benchmark = index(input.benchmark());
    List<LocalDate> calendar = input.tradingDates().stream().sorted().toList();
    Map<String, Map<String, SignalStatisticView>> result = new LinkedHashMap<>();
    for (int horizon : List.of(5, 10, 20)) {
      Map<String, SignalStatisticView> rules = new TreeMap<>();
      groups.forEach(
          (code, events) ->
              rules.put(
                  code,
                  group(input, events, new Window(horizon, cutoff, calendar, bars, benchmark))));
      result.put(horizon + "D", rules);
    }
    return result;
  }

  private static SignalStatisticView group(
      SignalEvaluationInput input, List<SignalEvent> events, Window window) {
    events.sort(
        Comparator.comparing(SignalEvent::getTradeDate)
            .thenComparing(event -> event.getId() == null ? 0 : event.getId()));
    Map<String, Integer> excluded = new TreeMap<>();
    List<Sample> accepted = new ArrayList<>();
    String selectedVersion = input.request().getRuleVersion();
    var versions = new TreeSet<String>();
    input.signals().stream()
        .filter(event -> event.getRuleCode().equals(events.getFirst().getRuleCode()))
        .forEach(event -> versions.add(version(event)));
    LocalDate lastExit = null;
    int triggered = 0;
    for (SignalEvent event : events) {
      ExecutionContext.checkpoint();
      if (!"TRIGGERED".equals(event.getStatus())) {
        exclude(excluded, "NOT_TRIGGERED");
        continue;
      }
      triggered++;
      if (Boolean.TRUE.equals(event.getSynthetic())) {
        exclude(excluded, "SYNTHETIC");
        continue;
      }
      if (selectedVersion != null && !selectedVersion.equals(version(event))) {
        exclude(excluded, "OTHER_RULE_VERSION");
        continue;
      }
      if (selectedVersion == null && versions.size() > 1) {
        exclude(excluded, "MIXED_RULE_VERSIONS");
        continue;
      }
      if (mode(input.request()) == AvailabilityMode.POINT_IN_TIME
          && (!known(event.getCreatedAt(), event.getUpdatedAt(), event.getTradeDate())
              || event.getRuleVersion() == null
              || event.getRevisionId() == null)) {
        exclude(excluded, "SIGNAL_NOT_AVAILABLE_AS_OF");
        continue;
      }
      int at = Collections.binarySearch(window.calendar(), event.getTradeDate());
      if (at < 0) {
        exclude(excluded, "CALENDAR_MISSING");
        continue;
      }
      if (at + window.horizon() >= window.calendar().size()
          || window.calendar().get(at + window.horizon()).isAfter(window.cutoff())) {
        exclude(excluded, "WAITING_WINDOW");
        continue;
      }
      LocalDate exit = window.calendar().get(at + window.horizon());
      if (lastExit != null && !event.getTradeDate().isAfter(lastExit)) {
        exclude(excluded, "OVERLAPPING_SIGNAL");
        continue;
      }
      List<LocalDate> dates = window.calendar().subList(at, at + window.horizon() + 1);
      if (!complete(window.bars(), dates)) {
        exclude(excluded, "PRICE_OR_FACTOR_GAP");
        continue;
      }
      if (mode(input.request()) == AvailabilityMode.POINT_IN_TIME
          && dates.stream().anyMatch(date -> !knownBar(window.bars().get(date), date))) {
        exclude(excluded, "PRICE_NOT_AVAILABLE_AS_OF");
        continue;
      }
      double value = change(window.bars().get(event.getTradeDate()), window.bars().get(exit));
      Double base =
          complete(window.benchmark(), dates)
                  && (mode(input.request()) != AvailabilityMode.POINT_IN_TIME
                      || dates.stream()
                          .allMatch(date -> knownBar(window.benchmark().get(date), date)))
              ? change(window.benchmark().get(event.getTradeDate()), window.benchmark().get(exit))
              : null;
      accepted.add(new Sample(value, base));
      lastExit = exit;
    }
    List<Double> returns = accepted.stream().map(Sample::value).sorted().toList();
    List<Sample> comparable =
        accepted.stream().filter(sample -> sample.benchmark() != null).toList();
    int size = returns.size();
    String status =
        size > 0
            ? size < input.minimumSamples() ? "SMALL_SAMPLE" : "OBSERVED"
            : triggered == 0
                ? "NO_TRIGGERED_SIGNALS"
                : excluded.getOrDefault("WAITING_WINDOW", 0) == triggered
                    ? "WAITING_WINDOW"
                    : "INSUFFICIENT_DATA";
    return SignalStatisticView.builder()
        .sampleCount(size)
        .meanReturn(mean(returns))
        .positiveRatio(
            size == 0 ? null : returns.stream().filter(value -> value > 0).count() / (double) size)
        .dataStatus(status)
        .medianReturn(size == 0 ? null : (returns.get((size - 1) / 2) + returns.get(size / 2)) / 2)
        .meanBenchmarkReturn(
            comparable.isEmpty()
                ? null
                : comparable.stream().mapToDouble(Sample::benchmark).average().orElseThrow())
        .meanExcessReturn(
            comparable.isEmpty()
                ? null
                : comparable.stream()
                    .mapToDouble(sample -> sample.value() - sample.benchmark())
                    .average()
                    .orElseThrow())
        .benchmarkSampleCount(comparable.size())
        .recordCount(events.size())
        .triggeredCount(triggered)
        .exclusions(excluded)
        .ruleVersions(List.copyOf(versions))
        .build();
  }

  private static AvailabilityMode mode(SignalStatisticsRequest request) {
    return request.getAvailabilityMode() == null
        ? AvailabilityMode.RETROSPECTIVE
        : request.getAvailabilityMode();
  }

  private static String version(SignalEvent event) {
    String version = event.getRuleVersion() == null ? "UNRECORDED" : event.getRuleVersion();
    if (event.getEvidenceJson() != null) {
      try {
        var evidence = new ObjectMapper().readTree(event.getEvidenceJson());
        var parameters = evidence.path("ruleParameters");
        if (parameters.isObject()) {
          return version + "@" + JsonFingerprint.hash(parameters.toString()).substring(0, 12);
        }
      } catch (IOException e) {
        return version + "@INVALID_PARAMETERS";
      }
    }
    return version;
  }

  private static boolean known(LocalDateTime created, LocalDateTime updated, LocalDate date) {
    LocalDateTime deadline = date.atTime(LocalTime.MAX);
    return created != null
        && !created.isAfter(deadline)
        && (updated == null || !updated.isAfter(deadline));
  }

  private static boolean knownBar(MarketBarDO bar, LocalDate date) {
    return bar.getRevisionId() != null && known(bar.getFetchedAt(), bar.getUpdatedAt(), date);
  }

  private static boolean complete(Map<LocalDate, MarketBarDO> bars, List<LocalDate> dates) {
    return dates.stream()
        .allMatch(
            date -> {
              MarketBarDO bar = bars.get(date);
              return bar != null
                  && !Boolean.TRUE.equals(bar.getSynthetic())
                  && bar.getClose() != null
                  && bar.getClose().signum() > 0
                  && bar.getFactor() != null
                  && bar.getFactor().signum() > 0;
            });
  }

  private static double change(MarketBarDO first, MarketBarDO last) {
    return last.getClose().doubleValue()
            * last.getFactor().doubleValue()
            / (first.getClose().doubleValue() * first.getFactor().doubleValue())
        - 1;
  }

  private static Map<LocalDate, MarketBarDO> index(List<MarketBarDO> values) {
    Map<LocalDate, MarketBarDO> result = new TreeMap<>();
    values.forEach(bar -> result.put(bar.getTradeDate(), bar));
    return result;
  }

  private static void exclude(Map<String, Integer> counts, String reason) {
    counts.merge(reason, 1, Integer::sum);
  }

  private static Double mean(List<Double> values) {
    return values.isEmpty()
        ? null
        : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
  }

  /**
   * 单规则观察窗口的对齐输入，不持有数据库连接。
   *
   * @author chenxuefeng
   */
  private record Window(
      /** 后续市场交易日数量。 */
      int horizon,
      /** 收益可用截止日。 */
      LocalDate cutoff,
      /** 权威开市日升序列表。 */
      List<LocalDate> calendar,
      /** 标的同源行情索引。 */
      Map<LocalDate, MarketBarDO> bars,
      /** 沪深300行情索引。 */
      Map<LocalDate, MarketBarDO> benchmark) {}

  /**
   * 一个完整且不重叠的描述统计样本。
   *
   * @author chenxuefeng
   */
  private record Sample(
      /** 标的收益，小数。 */
      double value,
      /** 对齐基准收益，小数；缺失时为空。 */
      Double benchmark) {}
}
