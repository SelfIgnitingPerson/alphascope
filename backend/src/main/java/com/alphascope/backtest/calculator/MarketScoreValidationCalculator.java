package com.alphascope.backtest.calculator;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.vo.MarketScoreView;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * 全A评分与后续基准收益的时间留出描述检验；不混入旧模型、未知分数或自选股表现。
 *
 * <p>同一窗口排除重叠，较早样本不能跨越较晚起点；相关性不等于显著性或因果性。
 *
 * @author chenxuefeng
 */
public final class MarketScoreValidationCalculator {
  private MarketScoreValidationCalculator() {}

  /**
   * 时间序列检验的完整只读输入，调用方可直接冻结复算。
   *
   * @author chenxuefeng
   */
  public record Input(
      /** 本次固定的研究区间起点。 */
      LocalDate start,
      /** 本次固定的研究区间终点。 */
      LocalDate end,
      /** 已保存评分记录，不能由指数趋势或自选收益替代。 */
      List<MarketScoreView> scores,
      /** 已保存沪深300原价及同源因子。 */
      List<MarketBarDO> benchmark,
      /** 权威开市日历，不按K线相邻行推断。 */
      Set<LocalDate> tradingDates,
      /** 单区间小样本提示线，不是有效性认证线。 */
      int minimumSamples) {}

  /**
   * 一项预设窗口的评分/未来收益配对结果。
   *
   * @author chenxuefeng
   */
  public record Result(
      /** EARLY较早或HOLDOUT较晚区间。 */
      String period,
      /** 信号后交易日数量。 */
      int horizon,
      /** NO_VALID_SCORES、INSUFFICIENT_PAIRS、SMALL_SAMPLE或DESCRIPTIVE_ONLY。 */
      String status,
      /** 完整、成熟且不重叠的评分/收益配对数量。 */
      int sampleCount,
      /** Spearman秩相关；恒定分数或恒定收益时为空。 */
      Double rankCorrelation,
      /** 固定低分组score<40的样本数。 */
      int lowCount,
      /** 固定低分组的后续基准平均收益。 */
      Double lowMeanReturn,
      /** 固定高分组score>=60的样本数。 */
      int highCount,
      /** 固定高分组的后续基准平均收益。 */
      Double highMeanReturn,
      /** 高分组减低分组平均收益；任一组为空时为空。 */
      Double highMinusLow,
      /** 当前所有完整评分中，保存晚于自身业务日结束的数量；不能作为严格时点证明。 */
      int savedAfterBusinessDay) {}

  /**
   * 按固定70%/30%日期切分检验5/10/20日窗口，无数据时输出明确原因。
   *
   * @param input 冻结评分、基准和日历
   * @return 两个日期区间各三个窗口的描述结果，不自动宣称方法已有效
   */
  public static List<Result> evaluate(Input input) {
    LocalDate split =
        input
            .start()
            .plusDays(Math.max(1, ChronoUnit.DAYS.between(input.start(), input.end()) * 7 / 10));
    var calendar = input.tradingDates().stream().sorted().toList();
    var prices = new TreeMap<LocalDate, MarketBarDO>();
    input.benchmark().forEach(bar -> prices.put(bar.getTradeDate(), bar));
    var scores =
        input.scores().stream()
            .filter(
                row ->
                    "MARKET_V3".equals(row.modelVersion())
                        && !Boolean.TRUE.equals(row.isSynthetic())
                        && !"UNKNOWN".equals(row.state())
                        && row.score() != null
                        && row.score().doubleValue() >= 0
                        && row.score().doubleValue() <= 100)
            .sorted(Comparator.comparing(MarketScoreView::tradeDate))
            .toList();
    var results = new ArrayList<Result>();
    for (String period : List.of("EARLY", "HOLDOUT")) {
      LocalDate start = period.equals("EARLY") ? input.start() : split;
      LocalDate end = period.equals("EARLY") ? split.minusDays(1) : input.end();
      var selected =
          scores.stream()
              .filter(row -> !row.tradeDate().isBefore(start) && !row.tradeDate().isAfter(end))
              .toList();
      int late =
          (int)
              selected.stream()
                  .filter(
                      row ->
                          row.createdAt() == null
                              || row.updatedAt() == null
                              || row.createdAt().toLocalDate().isAfter(row.tradeDate())
                              || row.updatedAt().toLocalDate().isAfter(row.tradeDate()))
                  .count();
      for (int horizon : List.of(5, 10, 20)) {
        var samples = new ArrayList<Pair>();
        LocalDate lastExit = null;
        for (var row : selected) {
          ExecutionContext.checkpoint();
          int at = Collections.binarySearch(calendar, row.tradeDate());
          if (at < 0 || at + horizon >= calendar.size()) continue;
          LocalDate exit = calendar.get(at + horizon);
          if (exit.isAfter(end) || lastExit != null && !row.tradeDate().isAfter(lastExit)) continue;
          var days = calendar.subList(at, at + horizon + 1);
          if (!complete(days, prices)) continue;
          var first = prices.get(row.tradeDate());
          var last = prices.get(exit);
          double change =
              last.getClose().doubleValue()
                      * last.getFactor().doubleValue()
                      / (first.getClose().doubleValue() * first.getFactor().doubleValue())
                  - 1;
          samples.add(new Pair(row.score().doubleValue(), change));
          lastExit = exit;
        }
        var low = samples.stream().filter(p -> p.score() < 40).toList();
        var high = samples.stream().filter(p -> p.score() >= 60).toList();
        Double lowMean = mean(low), highMean = mean(high);
        results.add(
            new Result(
                period,
                horizon,
                selected.isEmpty()
                    ? "NO_VALID_SCORES"
                    : samples.isEmpty()
                        ? "INSUFFICIENT_PAIRS"
                        : samples.size() < input.minimumSamples()
                            ? "SMALL_SAMPLE"
                            : "DESCRIPTIVE_ONLY",
                samples.size(),
                correlation(samples),
                low.size(),
                lowMean,
                high.size(),
                highMean,
                lowMean == null || highMean == null ? null : highMean - lowMean,
                late));
      }
    }
    return List.copyOf(results);
  }

  private static boolean complete(List<LocalDate> dates, TreeMap<LocalDate, MarketBarDO> prices) {
    String source = null;
    for (LocalDate date : dates) {
      var bar = prices.get(date);
      if (bar == null
          || Boolean.TRUE.equals(bar.getSynthetic())
          || bar.getSource() == null
          || bar.getClose() == null
          || bar.getClose().signum() <= 0
          || bar.getFactor() == null
          || bar.getFactor().signum() <= 0) return false;
      if (source != null && !source.equals(bar.getSource())) return false;
      source = bar.getSource();
    }
    return true;
  }

  private static Double mean(List<Pair> pairs) {
    return pairs.isEmpty()
        ? null
        : pairs.stream().mapToDouble(Pair::change).average().orElseThrow();
  }

  private static Double correlation(List<Pair> pairs) {
    if (pairs.size() < 3) return null;
    var x = ranks(pairs.stream().map(Pair::score).toList());
    var y = ranks(pairs.stream().map(Pair::change).toList());
    double center = (pairs.size() + 1) / 2.0, xx = 0, yy = 0, xy = 0;
    for (int i = 0; i < pairs.size(); i++) {
      double a = x.get(i) - center, b = y.get(i) - center;
      xx += a * a;
      yy += b * b;
      xy += a * b;
    }
    return xx == 0 || yy == 0 ? null : xy / Math.sqrt(xx * yy);
  }

  private static List<Double> ranks(List<Double> values) {
    var ordered = values.stream().sorted().toList();
    var ranks = new TreeMap<Double, Double>();
    for (int i = 0; i < ordered.size(); ) {
      int end = i + 1;
      while (end < ordered.size() && Double.compare(ordered.get(i), ordered.get(end)) == 0) end++;
      ranks.put(ordered.get(i), (i + 1 + end) / 2.0);
      i = end;
    }
    return values.stream().map(ranks::get).toList();
  }

  /**
   * 一个完整评分与同日开始的未来收益配对。
   *
   * @author chenxuefeng
   */
  private record Pair(double score, double change) {}
}
