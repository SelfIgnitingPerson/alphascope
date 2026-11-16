package com.alphascope.indicator.calculator;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.indicator.dto.TechnicalWindow;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 唯一技术规则证据计算：均线HALF_UP四位，RS及量比八位，生产、回顾和离线复算共用。
 *
 * <p>每项按自己的最短窗口核验交易日缺口；未知缺口不跳过，未来价格不参与。
 *
 * @author chenxuefeng
 */
public final class TechnicalEvidenceCalculator {
  public static final String VERSION = "TECHNICAL_V2";

  private TechnicalEvidenceCalculator() {}

  /**
   * 计算当日及前一期证据，缺失指标不写入返回文档。
   *
   * @param window 固定业务日、同源行情和权威交易日
   * @return 可扩展规则证据；均线和比例已经按数据库精度统一
   */
  public static Map<String, Double> calculate(TechnicalWindow window) {
    ExecutionContext.checkpoint();
    var stock = index(window.bars());
    var base = index(window.benchmark());
    var past = stock.headMap(window.date(), true).values().stream().toList();
    if (past.isEmpty()
        || !window.date().equals(past.getLast().getTradeDate())
        || !window.expectedDates().contains(window.date())) return Map.of();
    past = past.subList(Math.max(0, past.size() - 61), past.size());
    double anchor = past.getLast().getFactor().doubleValue();
    var closes =
        past.stream()
            .map(bar -> bar.getClose().doubleValue() * bar.getFactor().doubleValue() / anchor)
            .toList();
    var values = new HashMap<String, Double>();
    values.put("close", closes.getLast());
    if (complete(past, window, 2)) {
      double previous = closes.get(closes.size() - 2);
      values.put("previousClose", previous);
      values.put("return", closes.getLast() / previous - 1);
    }
    if (complete(past, window, 21)) {
      var prior = closes.subList(closes.size() - 21, closes.size() - 1);
      values.put(
          "previous20Low", prior.stream().mapToDouble(Double::doubleValue).min().orElseThrow());
      var volumes = past.stream().map(bar -> bar.getVolume().doubleValue()).toList();
      copy(values, "volumeRatio", IndicatorCalculator.volumeRatio(volumes, 20), 8);
    }
    for (int size : List.of(20, 60)) {
      if (complete(past, window, size))
        copy(values, "ma" + size, IndicatorCalculator.ma(closes, size), 4);
      if (complete(past, window, size + 1)) {
        copy(
            values,
            "previousMa" + size,
            IndicatorCalculator.ma(closes.subList(0, closes.size() - 1), size),
            4);
      }
    }
    if (complete(past, window, 21)) {
      var aligned = new ArrayList<Double>();
      for (var bar : past) {
        var benchmark = base.get(bar.getTradeDate());
        aligned.add(
            benchmark == null
                ? null
                : benchmark.getClose().doubleValue() * benchmark.getFactor().doubleValue());
      }
      int size = closes.size();
      if (aligned.subList(size - 21, size).stream().noneMatch(Objects::isNull)) {
        copy(
            values,
            "rs20",
            IndicatorCalculator.rs(
                closes.subList(size - 21, size), aligned.subList(size - 21, size), 20),
            8);
      }
      if (complete(past, window, 22)
          && aligned.subList(size - 22, size - 1).stream().noneMatch(Objects::isNull)) {
        copy(
            values,
            "previousRs20",
            IndicatorCalculator.rs(
                closes.subList(size - 22, size - 1), aligned.subList(size - 22, size - 1), 20),
            8);
      }
    }
    return Map.copyOf(values);
  }

  /**
   * 统一写库和判定的数值精度。
   *
   * @param value 有限数值
   * @param scale 小数位数
   * @return HALF_UP舍入后的十进制数
   */
  public static BigDecimal rounded(double value, int scale) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("技术指标不是有限数值");
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
  }

  private static boolean complete(List<MarketBarDO> bars, TechnicalWindow window, int count) {
    if (bars.size() < count) return false;
    var selected = bars.subList(bars.size() - count, bars.size());
    var first = selected.getFirst().getTradeDate();
    var observed = selected.stream().map(MarketBarDO::getTradeDate).collect(Collectors.toSet());
    var expected =
        window.expectedDates().stream()
            .filter(day -> !day.isBefore(first) && !day.isAfter(window.date()))
            .collect(Collectors.toSet());
    return expected.equals(observed);
  }

  private static TreeMap<LocalDate, MarketBarDO> index(List<MarketBarDO> rows) {
    var result = new TreeMap<LocalDate, MarketBarDO>();
    String source = null;
    for (var row : rows) {
      if (row.getTradeDate() == null
          || row.getSource() == null
          || row.getSource().isBlank()
          || row.getClose() == null
          || row.getClose().signum() <= 0
          || row.getFactor() == null
          || row.getFactor().signum() <= 0
          || row.getVolume() == null
          || row.getVolume().signum() < 0) {
        throw new IllegalArgumentException("技术窗口缺少明确来源、日期、价格、因子或成交量");
      }
      if (source != null && !source.equals(row.getSource()))
        throw new IllegalArgumentException("技术窗口不能混合来源");
      source = row.getSource();
      if (result.put(row.getTradeDate(), row) != null)
        throw new IllegalArgumentException("技术窗口同日行情重复");
    }
    return result;
  }

  private static void copy(
      Map<String, Double> target, String key, OptionalDouble value, int scale) {
    value.ifPresent(number -> target.put(key, rounded(number, scale).doubleValue()));
  }
}
