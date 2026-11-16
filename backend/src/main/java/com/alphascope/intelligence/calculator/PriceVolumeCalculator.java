package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.dto.PositionStrategyInput;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.intelligence.vo.PriceVolumeStudy.Zone;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 两个自然月的可复核量价研究；参考位只从截止日前行情形成，截止日用于验证突破与失守。
 *
 * <p>日K价桶不等于真实筹码分布。阈值为明确的研究假设，未通过历史效果验证。
 *
 * @author chenxuefeng
 */
public final class PriceVolumeCalculator {
  public static final String VERSION = "PRICE_VOLUME_2M_V3";
  private static final List<String> NOTES =
      List.of(
          "窗口为截止日减两个自然月至截止日，至少30根有效日K；应有交易日必须完整，明确停牌由同源证据扣除。",
          "价格=原价×当期因子/截止日因子；量能使用人民币成交额，不把价格复权因子当作股本因子。",
          "支撑取最近20根历史K线内已确认的2左2右波谷，否则取前20期最低价；不使用截止日重设已失守支撑。",
          "止损参考=原支撑下沿−0.5×前14期简单平均真实波幅；仅研究参考，跳空、涨跌停或交易制度可能使其无法执行。",
          "压力来自已确认波峰、两月前高及前三个日K近似成交额价桶；价桶宽=max(收盘价1%, ATR14/2)，不是逐笔筹码成本。",
          "5日收益±2%区分明显方向，5日量比≥1.2为放量、≤0.8为缩量；靠近压力定义为距离下沿不超过2%。",
          "没有个人持仓、买入成本和风险承受度，不给减仓比例；未满足减仓条件不代表适合买入或没有风险。");

  private PriceVolumeCalculator() {}

  /**
   * 从同一份冻结窗口计算参考价位与条件式意见，输入不足时不输出价格或减仓判断。
   *
   * @param window 明确截止日、同源行情、因子和日历
   * @return 包含证据、状态和方法边界的不可变结果
   */
  public static PriceVolumeStudy calculate(PriceVolumeWindow window) {
    if (window.synthetic()) {
      return unavailable(window, "DEMO", "演示行情不生成真实价位和减仓判断");
    }
    if (window.suspended()) {
      return unavailable(window, "SUSPENDED", "截止日确认停牌，需恢复有效交易后重新评估");
    }
    try {
      List<Bar> bars = normalize(window);
      if (bars.isEmpty() || !bars.getLast().date().equals(window.date())) {
        return unavailable(window, "STALE", "缺少截止日有效行情，不能用旧价格提出当前减仓意见");
      }
      Set<LocalDate> expected = window.expectedDates();
      if (expected == null || expected.isEmpty()) {
        return unavailable(window, "INSUFFICIENT_DATA", "未冻结可核验交易日历");
      }
      if (expected.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("交易日历含空日期");
      }
      Set<LocalDate> actual = bars.stream().map(Bar::date).collect(Collectors.toSet());
      Set<LocalDate> required =
          expected.stream()
              .filter(d -> !d.isBefore(window.date().minusMonths(2)) && !d.isAfter(window.date()))
              .collect(Collectors.toSet());
      if (!required.equals(actual)) {
        var missing = required.stream().filter(d -> !actual.contains(d)).sorted().toList();
        throw new IllegalArgumentException(
            "交易日不一致；缺少"
                + missing.size()
                + "日："
                + missing.stream().limit(5).toList()
                + "；额外或未核验日期"
                + actual.stream().filter(d -> !required.contains(d)).sorted().limit(5).toList());
      }
      if (bars.size() < 30) {
        throw new IllegalArgumentException("近两个月仅" + bars.size() + "根有效K线，至少需要30根");
      }
      return analyze(window, bars, required.size());
    } catch (IllegalArgumentException e) {
      return unavailable(window, "INSUFFICIENT_DATA", e.getMessage());
    }
  }

  private static PriceVolumeStudy analyze(PriceVolumeWindow window, List<Bar> bars, int expected) {
    Bar last = bars.getLast();
    List<Bar> prior = bars.subList(0, bars.size() - 1);
    double atr = atr(prior);
    double pad = Math.max(0.01, Math.max(prior.getLast().close() * 0.005, atr * 0.25));
    Zone support = support(prior, pad);
    double candidateStop = price(support.lower() - 0.5 * atr);
    Double stop = candidateStop > 0 ? candidateStop : null;
    String stopStatus =
        stop == null ? "UNAVAILABLE" : last.close() <= stop ? "BREACHED" : "ABOVE_REFERENCE";
    List<Zone> resistance = resistance(prior, last.close(), atr, pad);
    int n = bars.size();
    double return5 = last.close() / bars.get(n - 6).close() - 1;
    double ratio = last.amount() / averageAmount(bars, n - 21, n - 1);
    double ratio5 = averageAmount(bars, n - 5, n) / averageAmount(bars, n - 25, n - 5);
    double ma20 = bars.subList(n - 20, n).stream().mapToDouble(Bar::close).average().orElseThrow();
    double dayReturn = last.close() / prior.getLast().close() - 1;
    boolean brokenSupport = last.close() < support.lower();
    boolean nearResistance =
        resistance.stream()
            .anyMatch(z -> last.close() >= z.lower() * 0.98 && last.close() <= z.upper());
    boolean weakSelling =
        (brokenSupport && dayReturn < 0 && ratio >= 1.2)
            || (return5 <= -0.02 && ratio5 >= 1.2 && last.close() < ma20);
    boolean shrinkingRally = nearResistance && return5 >= 0.02 && ratio5 <= 0.8;
    String decision;
    String conclusion;
    if ("BREACHED".equals(stopStatus)) {
      decision = "REDUCE_REVIEW";
      conclusion = "已触及或跌破原止损参考位，优先评估减仓控制风险；不能向下重设参考位来延后处理。";
    } else if (weakSelling) {
      decision = "REDUCE_REVIEW";
      conclusion = "出现放量走弱并失守支撑或均线的组合，已有持仓可考虑减仓；核对下一期是否收复关键位置。";
    } else if (shrinkingRally) {
      decision = "PROFIT_PROTECTION_REVIEW";
      conclusion = "反弹接近压力区但成交额收缩，已有持仓可评估分批减仓；若放量突破，需重新判断。";
    } else if (brokenSupport || (return5 <= -0.02 && last.close() < ma20)) {
      decision = "WATCH_REDUCTION";
      conclusion = "走势偏弱，减仓的放量确认尚不充分；重点核对支撑失守及止损参考，不能将缩量下跌理解为风险解除。";
    } else {
      decision = "OBSERVE";
      conclusion = "尚未出现本方法定义的明确减仓组合，继续观察压力、支撑与量能变化；这不等于可以买入或必须持有。";
    }
    List<String> evidence = new ArrayList<>();
    evidence.add(
        "最近5日复权收益" + percent(return5) + "；独立5/20日成交额均量比" + value(ratio5) + "倍（≥1.2放量、≤0.8缩量）");
    evidence.add(
        "当日成交额量比" + value(ratio) + "倍；当日复权收益" + percent(dayReturn) + "；MA20为" + value(ma20) + "元");
    evidence.add(
        "原支撑"
            + support.lower()
            + "—"
            + support.upper()
            + "元；收盘"
            + value(last.close())
            + "元"
            + (brokenSupport ? "已低于支撑下沿" : "未低于支撑下沿"));
    evidence.add(
        stop == null
            ? "波动缓冲后无法形成正数止损参考位"
            : "止损参考" + stop + "元；" + ("BREACHED".equals(stopStatus) ? "已触及或跌破" : "尚未触及"));
    if (resistance.isEmpty()) {
      evidence.add("收盘已不低于此前两月高点，上方没有本窗口可验证的压力样本，不外推目标价");
    }
    var study =
        result(window)
            .status("AVAILABLE")
            .observedDays(n)
            .expectedDays(expected)
            .actualStart(bars.getFirst().date())
            .source(last.source())
            .close(price(last.close()))
            .return5(rounded(return5, 8))
            .volumeRatio(rounded(ratio, 4))
            .volumeRatio5(rounded(ratio5, 4))
            .atr14(price(atr))
            .ma20(price(ma20))
            .support(support)
            .stopReference(stop)
            .stopStatus(stopStatus)
            .resistances(resistance)
            .decision(decision)
            .conclusion(conclusion)
            .evidence(List.copyOf(evidence))
            .missing(List.of())
            .build();
    List<Bar> beforeConfirmation = bars.subList(0, bars.size() - 2);
    double earlierAtr = atr(beforeConfirmation);
    double earlierPad =
        Math.max(0.01, Math.max(beforeConfirmation.getLast().close() * 0.005, earlierAtr * 0.25));
    var strategies =
        PositionStrategyCalculator.calculate(
            PositionStrategyInput.builder()
                .study(study)
                .previousClose(prior.getLast().close())
                .currentLow(last.low())
                .dayReturn(dayReturn)
                .breakoutReference(strategyReference(prior, atr, pad))
                .addReference(strategyReference(beforeConfirmation, earlierAtr, earlierPad))
                .breakoutReferenceAsOf(prior.getLast().date())
                .addReferenceAsOf(beforeConfirmation.getLast().date())
                .stopTriggered(stop == null ? null : "BREACHED".equals(stopStatus))
                .weakSelling(weakSelling)
                .shrinkingRally(shrinkingRally)
                .build());
    return study.toBuilder().strategies(strategies).build();
  }

  /** 策略参考使用确认之前的完整窗口；已在样本高点时仍保留前高区，不用本期新高重设门槛。 */
  private static Zone strategyReference(List<Bar> history, double atr, double pad) {
    var candidates = resistance(history, history.getLast().close(), atr, pad);
    if (!candidates.isEmpty()) {
      return candidates.getFirst();
    }
    var highest = history.stream().max(Comparator.comparingDouble(Bar::high)).orElseThrow();
    return zone(highest.high(), pad, "TWO_MONTH_HIGH", highest.date());
  }

  private static List<Bar> normalize(PriceVolumeWindow window) {
    var indexed = new TreeMap<LocalDate, MarketBarDO>();
    for (var row : window.bars() == null ? List.<MarketBarDO>of() : window.bars()) {
      if (row == null || row.getTradeDate() == null) {
        throw new IllegalArgumentException("存在缺少交易日期的行情");
      }
      if (row.getTradeDate().isBefore(window.date().minusMonths(2))
          || row.getTradeDate().isAfter(window.date())) {
        continue;
      }
      if (indexed.put(row.getTradeDate(), row) != null) {
        throw new IllegalArgumentException("同一交易日行情重复：" + row.getTradeDate());
      }
    }
    if (indexed.isEmpty()) {
      return List.of();
    }
    double anchor = positive(indexed.lastEntry().getValue().getFactor(), "截止日复权因子");
    String source = null;
    var result = new ArrayList<Bar>();
    for (var row : indexed.values()) {
      if (!Boolean.FALSE.equals(row.getSynthetic())) {
        throw new IllegalArgumentException("行情缺少真实模式证明");
      }
      if (row.getSource() == null
          || row.getSource().isBlank()
          || source != null && !source.equals(row.getSource())) {
        throw new IllegalArgumentException("行情缺少来源或混合来源");
      }
      source = row.getSource();
      double factor = positive(row.getFactor(), "复权因子");
      double adjustment = factor / anchor;
      double close = positive(row.getClose(), "收盘价") * adjustment;
      double high = positive(row.getHigh(), "最高价") * adjustment;
      double low = positive(row.getLow(), "最低价") * adjustment;
      double open = positive(row.getOpen(), "开盘价") * adjustment;
      positive(row.getVolume(), "原始成交股数");
      double amount = positive(row.getAmount(), "成交金额");
      if (Math.min(Math.min(low, close), Math.min(open, amount)) <= 0
          || !Double.isFinite(high + low + close + open + amount)
          || high < Math.max(open, close)
          || low > Math.min(open, close)
          || high < low) {
        throw new IllegalArgumentException("OHLC范围或数值无效：" + row.getTradeDate());
      }
      result.add(new Bar(row.getTradeDate(), high, low, close, amount, source));
    }
    return result;
  }

  private static Zone support(List<Bar> prior, double pad) {
    for (int i = prior.size() - 3; i >= Math.max(2, prior.size() - 20); i--) {
      if (pivot(prior, i, false)) {
        return zone(prior.get(i).low(), pad, "RECENT_SWING_LOW", prior.get(i).date());
      }
    }
    Bar low =
        prior.subList(prior.size() - 20, prior.size()).stream()
            .min(Comparator.comparingDouble(Bar::low))
            .orElseThrow();
    return zone(low.low(), pad, "PRIOR_20_LOW", low.date());
  }

  private static List<Zone> resistance(List<Bar> prior, double close, double atr, double pad) {
    var candidates = new ArrayList<Zone>();
    Bar high = prior.stream().max(Comparator.comparingDouble(Bar::high)).orElseThrow();
    if (close >= high.high()) {
      return List.of();
    }
    candidates.add(zone(high.high(), pad, "TWO_MONTH_HIGH", high.date()));
    for (int i = 2; i < prior.size() - 2; i++) {
      if (pivot(prior, i, true)) {
        candidates.add(zone(prior.get(i).high(), pad, "CONFIRMED_SWING_HIGH", prior.get(i).date()));
      }
    }
    double width = Math.max(0.01, Math.max(close * 0.01, atr * 0.5));
    Map<Long, List<Bar>> buckets = new HashMap<>();
    for (var bar : prior) {
      long bucket = (long) Math.floor((bar.high() + bar.low() + bar.close()) / 3 / width);
      buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(bar);
    }
    double totalAmount = prior.stream().mapToDouble(Bar::amount).sum();
    buckets.entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<Long, List<Bar>>>comparingDouble(
                    e -> e.getValue().stream().mapToDouble(Bar::amount).sum())
                .reversed()
                .thenComparingLong(Map.Entry::getKey))
        .limit(3)
        .forEach(
            entry -> {
              double lower = entry.getKey() * width;
              candidates.add(
                  new Zone(
                      price(Math.max(0.01, lower)),
                      price(lower + width),
                      "DAILY_VOLUME_ZONE",
                      entry.getValue().stream().map(Bar::date).toList(),
                      rounded(
                          entry.getValue().stream().mapToDouble(Bar::amount).sum() / totalAmount,
                          8)));
            });
    var selected = new ArrayList<Zone>();
    for (var candidate :
        candidates.stream()
            .filter(z -> z.upper() > close && (z.lower() + z.upper()) / 2 > close)
            .sorted(Comparator.comparingDouble(Zone::lower).thenComparing(Zone::basis))
            .toList()) {
      // 相邻重叠区间不重复展示；保留可追溯的具体证据类别。
      if (selected.stream()
          .noneMatch(z -> candidate.lower() <= z.upper() && candidate.upper() >= z.lower())) {
        selected.add(candidate);
      }
      if (selected.size() == 3) {
        break;
      }
    }
    return List.copyOf(selected);
  }

  private static boolean pivot(List<Bar> bars, int index, boolean high) {
    double center = high ? bars.get(index).high() : bars.get(index).low();
    boolean strict = false;
    for (int i = index - 2; i <= index + 2; i++) {
      if (i == index) {
        continue;
      }
      double value = high ? bars.get(i).high() : bars.get(i).low();
      if (high ? value > center : value < center) {
        return false;
      }
      strict |= value != center;
    }
    return strict;
  }

  private static double atr(List<Bar> prior) {
    double sum = 0;
    for (int i = prior.size() - 14; i < prior.size(); i++) {
      Bar bar = prior.get(i);
      double previous = prior.get(i - 1).close();
      sum +=
          Math.max(
              bar.high() - bar.low(),
              Math.max(Math.abs(bar.high() - previous), Math.abs(bar.low() - previous)));
    }
    return sum / 14;
  }

  private static Zone zone(double center, double pad, String basis, LocalDate date) {
    return new Zone(
        price(Math.max(0.01, center - pad)), price(center + pad), basis, List.of(date), null);
  }

  private static double averageAmount(List<Bar> bars, int start, int end) {
    return bars.subList(start, end).stream().mapToDouble(Bar::amount).average().orElseThrow();
  }

  private static double positive(Number number, String field) {
    if (number == null || !Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0) {
      throw new IllegalArgumentException("缺少有效" + field);
    }
    return number.doubleValue();
  }

  private static double rounded(double number, int scale) {
    return BigDecimal.valueOf(number).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }

  private static double price(double number) {
    return rounded(number, 2);
  }

  private static String value(double number) {
    return String.format(Locale.ROOT, "%.2f", number);
  }

  private static String percent(double number) {
    return value(number * 100) + "%";
  }

  private static PriceVolumeStudy unavailable(
      PriceVolumeWindow window, String status, String reason) {
    var study =
        result(window)
            .status(status)
            .observedDays(0)
            .expectedDays(window.expectedDates() == null ? 0 : window.expectedDates().size())
            .stopStatus("UNAVAILABLE")
            .resistances(List.of())
            .decision("UNAVAILABLE")
            .conclusion("证据不足，暂不提供压力、止损或减仓判断。")
            .evidence(List.of())
            .missing(List.of(reason))
            .build();
    return study.toBuilder().strategies(PositionStrategyCalculator.unavailable(study)).build();
  }

  /** 固定的版本、区间和方法说明只组装一次，其余字段由成功或缺项分支明确填写。 */
  private static PriceVolumeStudy.PriceVolumeStudyBuilder result(PriceVolumeWindow window) {
    return PriceVolumeStudy.builder()
        .version(VERSION)
        .symbol(window.symbol())
        .name(window.name())
        .windowStart(window.date().minusMonths(2))
        .asOf(window.date())
        .methodNotes(NOTES)
        .volumeBasis("TURNOVER_AMOUNT");
  }

  /**
   * 截止日锚点的规范化价格与人民币成交额，供纯计算内部使用。
   *
   * @author chenxuefeng
   */
  private record Bar(
      /** 有效交易日期。 */
      LocalDate date,
      /** 最高价，元。 */
      double high,
      /** 最低价，元。 */
      double low,
      /** 收盘价，元。 */
      double close,
      /** 原始人民币成交金额；不按价格因子调整。 */
      double amount,
      /** 原价和因子的共同来源。 */
      String source) {}
}
