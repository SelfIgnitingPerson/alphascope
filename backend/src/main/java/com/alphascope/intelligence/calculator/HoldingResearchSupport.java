package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 三周期研究共用的同源窗口校验与精度工具，公式保持不变。
 *
 * @author chenxuefeng
 */
final class HoldingResearchSupport {
  private HoldingResearchSupport() {}

  static final List<String> ASSUMPTIONS =
      List.of(
          "行情条件满足后仍需确认个人计划、资金与可承受损失；本视图不读取个人持仓或给出数量比例。",
          "参考位按收盘证据核对，跳空、涨跌停、费用和实际成交可能使执行价格偏离参考价。",
          "买入与加仓须分别满足条件；新增风险优先于继续买入，不能把短期交易变成长持来回避退出条件。");

  static List<Bar> checkedBars(HoldingResearchInput input, int count) {
    var eligible =
        input.stock().history().stream()
            .filter(b -> b.getTradeDate() != null && !b.getTradeDate().isAfter(input.asOf()))
            .sorted(Comparator.comparing(MarketBarDO::getTradeDate))
            .toList();
    if (eligible.size() < count)
      throw new IllegalArgumentException("需至少" + count + "个有效交易日，当前仅" + eligible.size() + "日");
    var tail = eligible.subList(eligible.size() - count, eligible.size());
    if (!tail.getLast().getTradeDate().equals(input.asOf()))
      throw new IllegalArgumentException("缺少本期行情");
    var sources = tail.stream().map(MarketBarDO::getSource).collect(Collectors.toSet());
    if (sources.size() != 1 || sources.contains(null) || sources.contains(""))
      throw new IllegalArgumentException("研究窗口行情与因子来源不一致");
    var dates = tail.stream().map(MarketBarDO::getTradeDate).collect(Collectors.toSet());
    var required =
        input.expectedDates().stream()
            .filter(d -> !d.isBefore(tail.getFirst().getTradeDate()) && !d.isAfter(input.asOf()))
            .collect(Collectors.toSet());
    if (dates.size() != tail.size() || !dates.equals(required))
      throw new IllegalArgumentException("研究窗口缺少连续交易日或日历，不能跳过缺日计算均线");
    double anchor = positive(tail.getLast().getFactor());
    var bars = new ArrayList<Bar>();
    for (var row : tail) {
      if (!Boolean.FALSE.equals(row.getSynthetic()))
        throw new IllegalArgumentException("窗口缺少真实模式证明");
      double ratio = positive(row.getFactor()) / anchor;
      double close = positive(row.getClose()) * ratio,
          low = positive(row.getLow()) * ratio,
          high = positive(row.getHigh()) * ratio;
      if (!Double.isFinite(close + low + high)
          || close <= 0
          || low <= 0
          || high <= 0
          || low > high
          || close < low
          || close > high) throw new IllegalArgumentException("历史OHLC关系无效");
      bars.add(new Bar(row.getTradeDate(), close, low, high));
    }
    return List.copyOf(bars);
  }

  static Horizon unavailable(String code, String reason) {
    return Horizon.builder()
        .code(code)
        .title("SHORT".equals(code) ? "短期交易" : "MEDIUM".equals(code) ? "中期趋势持有" : "长期经营持有")
        .period("SHORT".equals(code) ? "1–20个交易日" : "MEDIUM".equals(code) ? "1–6个月" : "6–24个月")
        .status("UNAVAILABLE")
        .conclusion("补齐证据后再形成该周期的买卖计划；当前不输出可执行价位。")
        .actions(List.of())
        .evidence(List.of())
        .missing(List.of(reason))
        .build();
  }

  static String state(List<Condition> conditions, String logic) {
    if ("ANY".equals(logic))
      return conditions.stream().anyMatch(c -> Boolean.TRUE.equals(c.met())) ? "WATCH" : "WAIT";
    return !conditions.isEmpty() && conditions.stream().allMatch(c -> Boolean.TRUE.equals(c.met()))
        ? "CONDITIONAL_MATCH"
        : "WAIT";
  }

  static double average(List<Bar> bars) {
    return bars.stream().mapToDouble(Bar::close).average().orElseThrow();
  }

  static double positive(Number value) {
    if (value == null || !Double.isFinite(value.doubleValue()) || value.doubleValue() <= 0)
      throw new IllegalArgumentException("历史价格或因子无效");
    return value.doubleValue();
  }

  static double money(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  static String value(Number value) {
    return value == null ? "未知" : String.format(Locale.ROOT, "%.2f", value.doubleValue());
  }

  static String percent(Number value) {
    return value == null ? "未知" : value(value.doubleValue() * 100) + "%";
  }

  /**
   * 同一截止日因子锚点的内部价格，不对外暴露。
   *
   * @author chenxuefeng
   */
  record Bar(LocalDate date, double close, double low, double high) {}
}
