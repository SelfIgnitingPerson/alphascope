package com.alphascope.indicator.calculator;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 无外部副作用的指标计算。输入必须按交易日升序且使用一致复权口径。 空 Optional 表示 INSUFFICIENT_HISTORY；调用方不得替换为零。
 *
 * @author chenxuefeng
 */
public final class IndicatorCalculator {
  private IndicatorCalculator() {}

  /** 简单均线取最后 n 个交易日；停牌/缺数不能补造零值。 */
  public static OptionalDouble ma(List<Double> closes, int n) {
    if (n <= 0) {
      throw new IllegalArgumentException("窗口必须为正数");
    }
    if (closes.size() < n) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(
        closes.subList(closes.size() - n, closes.size()).stream()
            .mapToDouble(IndicatorCalculator::valid)
            .average()
            .orElseThrow());
  }

  /** 当日成交量除以前 n 日均量，严格排除当天；零基线无法计算。 */
  public static OptionalDouble volumeRatio(List<Double> volumes, int n) {
    if (n <= 0) {
      throw new IllegalArgumentException("窗口必须为正数");
    }
    if (volumes.size() <= n) {
      return OptionalDouble.empty();
    }
    double baseline = ma(volumes.subList(0, volumes.size() - 1), n).orElseThrow();
    return baseline > 0
        ? OptionalDouble.of(valid(volumes.get(volumes.size() - 1)) / baseline)
        : OptionalDouble.empty();
  }

  /** 相对强度必须传入同日期对齐的标的/基准序列。 */
  public static OptionalDouble rs(List<Double> closes, List<Double> benchmark, int n) {
    if (n <= 0 || closes.size() != benchmark.size()) {
      throw new IllegalArgumentException("窗口或日期对齐不合法");
    }
    int t = closes.size() - 1;
    if (t < n) {
      return OptionalDouble.empty();
    }
    double start = valid(closes.get(t - n)),
        bStart = valid(benchmark.get(t - n)),
        bEnd = valid(benchmark.get(t));
    if (start <= 0 || bStart <= 0 || bEnd <= 0) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of((valid(closes.get(t)) / start) / (bEnd / bStart) - 1);
  }

  /** Wilder ATR：首 n 个 TR 算术平均初始化，随后 (前ATR*(n-1)+TR)/n。 */
  public static OptionalDouble atr(
      List<Double> highs, List<Double> lows, List<Double> closes, int n) {
    if (n <= 0 || highs.size() != lows.size() || highs.size() != closes.size()) {
      throw new IllegalArgumentException("序列长度不匹配");
    }
    if (closes.size() <= n) {
      return OptionalDouble.empty();
    }
    double atr = 0;
    for (int i = 1; i < closes.size(); i++) {
      double high = valid(highs.get(i)), low = valid(lows.get(i)), prev = valid(closes.get(i - 1));
      if (high < low) {
        throw new IllegalArgumentException("最高价小于最低价");
      }
      double tr = Math.max(high - low, Math.max(Math.abs(high - prev), Math.abs(low - prev)));
      if (i <= n) {
        atr += tr / n;
      } else {
        atr = (atr * (n - 1) + tr) / n;
      }
    }
    return OptionalDouble.of(atr);
  }

  private static double valid(Double value) {
    if (value == null || !Double.isFinite(value) || value < 0) {
      throw new IllegalArgumentException("数据缺失或非法");
    }
    return value;
  }
}
