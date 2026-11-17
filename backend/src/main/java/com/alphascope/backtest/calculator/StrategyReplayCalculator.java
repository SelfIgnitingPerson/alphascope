package com.alphascope.backtest.calculator;

import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.vo.StrategySimulation;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 使用生产两月量价条件，收盘决定、次个市场交易日开盘尝试执行；不优化参数。
 *
 * @author chenxuefeng
 */
public final class StrategyReplayCalculator {
  public static final String VERSION = "PRICE_VOLUME_EXECUTION_V1";
  private static final double CAPITAL = 100000,
      COMMISSION = 0.0003,
      STAMP = 0.0005,
      SLIPPAGE = 0.0005;

  private StrategyReplayCalculator() {}

  /**
   * 从冻结原价、因子与日历回顾真实生产条件，区间不足不报告收益。
   *
   * @param input 已冻结的技术验证输入
   * @return 三个区间及不可省略的执行假设
   */
  public static StrategySimulation evaluate(TechnicalReplayInput input) {
    var request = input.source().request();
    var start = request.getStart();
    var end = request.getEnd();
    LocalDate split =
        request.getSplitDate() == null
            ? start.plusDays(Math.max(1, ChronoUnit.DAYS.between(start, end) * 7 / 10))
            : request.getSplitDate();
    var periods = new ArrayList<StrategySimulation.Period>();
    periods.add(period(input, "ALL", start, end));
    if (split.isAfter(start) && !split.isAfter(end)) {
      periods.add(period(input, "DISCOVERY", start, split.minusDays(1)));
      periods.add(period(input, "HOLDOUT", split, end));
    }
    return new StrategySimulation(
        VERSION,
        Map.of(
            "initialCapital",
            CAPITAL,
            "commissionRate",
            COMMISSION,
            "stampDutySellRate",
            STAMP,
            "slippageRate",
            SLIPPAGE,
            "minimumCommission",
            5.0,
            "maximumAmountParticipation",
            0.01,
            "lotSize",
            100.0,
            "initialAllocation",
            0.25),
        List.copyOf(periods),
        List.of(
            "当前价格版本回顾，非严格历史时点；当前自选有选择与幸存者偏差。",
            "建仓25%、加仓25%、减仓50%、风险线触及后下一交易日退出；这些固定参数不是个人仓位建议，也未优化。",
            "单价日、开盘相对前收涨跌达到4.5%的日期保守拒绝执行；没有历史ST及精确涨跌停资料，不能证明所有其他日期可成交。",
            "成交额参与上限1%、100股整手、最低佣金5元；滑点为假设，不是真实逐笔成交。",
            "持仓中发生复权因子变化时区间标为公司行动证据不足，不猜测分红或送转账务。",
            "较晚区间独立起仓；有限样本和回顾结果不构成有效性或收益承诺。"));
  }

  private static StrategySimulation.Period period(
      TechnicalReplayInput input, String label, LocalDate start, LocalDate end) {
    var prices = new TreeMap<LocalDate, MarketBarDO>();
    for (var b : input.source().bars())
      if (!b.getTradeDate().isAfter(end)) {
        if (prices.put(b.getTradeDate(), b) != null)
          return invalid(label, start, end, "重复日线", 0, 0, 0, false);
      }
    var days =
        input.source().tradingDates().stream()
            .filter(d -> !d.isBefore(start) && !d.isAfter(end))
            .sorted()
            .toList();
    if (days.size() < 2) return invalid(label, start, end, "权威交易日历不足", 0, 0, 0, false);
    String source = null;
    double cash = CAPITAL,
        shares = 0,
        entryFactor = 0,
        stop = 0,
        fees = 0,
        peak = CAPITAL,
        drawdown = 0;
    int completed = 0, skipped = 0, pending = 0;
    double pendingStop = 0, lastClose = 0;
    Set<LocalDate> stockDates = input.stockTradingDates();
    for (var date : days) {
      ExecutionContext.checkpoint();
      var bar = prices.get(date);
      if (bar == null) {
        if (stockDates.contains(date))
          return invalid(label, start, end, "应有交易日缺少行情", completed, skipped, fees, shares > 0);
        if (pending != 0) {
          skipped++;
          pending = 0;
        }
        continue;
      }
      if (!Boolean.FALSE.equals(bar.getSynthetic())
          || bar.getFactor() == null
          || bar.getFactor().signum() <= 0
          || bar.getOpen() == null
          || bar.getClose() == null
          || bar.getAmount() == null
          || bar.getAmount().signum() <= 0)
        return invalid(label, start, end, "原价、因子或成交额不合格", completed, skipped, fees, shares > 0);
      if (source == null) source = bar.getSource();
      if (source == null || !source.equals(bar.getSource()))
        return invalid(label, start, end, "窗口混合来源", completed, skipped, fees, shares > 0);
      double factor = bar.getFactor().doubleValue(),
          open = bar.getOpen().doubleValue(),
          close = bar.getClose().doubleValue();
      if (shares > 0 && Math.abs(factor / entryFactor - 1) > 0.00000001)
        return invalid(label, start, end, "持仓期间复权因子改变，缺少可靠公司行动账务", completed, skipped, fees, true);
      if (pending != 0) {
        boolean locked =
            bar.getHigh() == null
                || bar.getLow() == null
                || bar.getHigh().compareTo(bar.getLow()) == 0
                || (lastClose > 0 && Math.abs(open / lastClose - 1) >= 0.045);
        if (locked) {
          skipped++;
        } else if (pending > 0) {
          double buy = open * (1 + SLIPPAGE);
          double budget =
              Math.min(Math.min(CAPITAL * 0.25, cash - 5), bar.getAmount().doubleValue() * 0.01);
          double quantity = Math.floor(Math.max(0, budget) / (buy * (1 + COMMISSION)) / 100) * 100;
          double commission = Math.max(5, quantity * buy * COMMISSION);
          if (quantity > 0 && quantity * buy + commission <= cash) {
            cash -= quantity * buy + commission;
            shares += quantity;
            fees += commission;
            entryFactor = factor;
            if (stop <= 0) stop = pendingStop;
          } else skipped++;
        } else if (shares > 0) {
          double quantity = pending == -2 ? shares : Math.floor(shares / 2 / 100) * 100;
          if (quantity == 0) quantity = shares;
          double sell = open * (1 - SLIPPAGE);
          double commission = Math.max(5, quantity * sell * COMMISSION) + quantity * sell * STAMP;
          if (quantity * sell <= bar.getAmount().doubleValue() * 0.01) {
            cash += quantity * sell - commission;
            shares -= quantity;
            fees += commission;
            completed++;
            if (shares == 0) stop = 0;
          } else skipped++;
        }
        pending = 0;
      }
      double equity = cash + shares * close;
      peak = Math.max(peak, equity);
      drawdown = Math.max(drawdown, 1 - equity / peak);
      var window =
          prices.values().stream()
              .filter(
                  b ->
                      !b.getTradeDate().isAfter(date)
                          && !b.getTradeDate().isBefore(date.minusMonths(2)))
              .toList();
      var expected =
          stockDates.stream()
              .filter(d -> !d.isAfter(date) && !d.isBefore(date.minusMonths(2)))
              .collect(java.util.stream.Collectors.toSet());
      var study =
          PriceVolumeCalculator.calculate(
              PriceVolumeWindow.builder()
                  .symbol(input.source().request().getSymbol())
                  .name(input.source().request().getSymbol())
                  .date(date)
                  .bars(window)
                  .expectedDates(expected)
                  .synthetic(false)
                  .suspended(false)
                  .build());
      if (shares > 0 && stop > 0 && close <= stop) pending = -2;
      else if (study.strategies() != null) {
        if (shares > 0
            && study.strategies().stream()
                .anyMatch(s -> s.code().equals("REDUCE") && s.status().equals("WATCH")))
          pending = -1;
        else if (shares == 0
            && study.strategies().stream()
                .anyMatch(
                    s -> s.code().startsWith("OPEN_") && s.status().equals("CONDITIONAL_MATCH"))) {
          pending = 1;
          pendingStop = study.stopReference() == null ? 0 : study.stopReference();
        } else if (shares > 0
            && shares * close < CAPITAL * 0.5
            && study.strategies().stream()
                .anyMatch(
                    s -> s.code().equals("ADD_RETEST") && s.status().equals("CONDITIONAL_MATCH")))
          pending = 1;
      }
      lastClose = close;
    }
    if (pending != 0) skipped++;
    double equity = cash + shares * lastClose;
    return new StrategySimulation.Period(
        label,
        start,
        end,
        "ASSUMPTION_BASED_REPLAY",
        completed,
        skipped,
        equity / CAPITAL - 1,
        drawdown,
        benchmark(input, start, end),
        fees,
        shares > 0,
        "收盘决策、下一交易日开盘；末日挂单未假定成交，末仓按收盘估值。涨跌停与ST历史尚未独立核验。");
  }

  private static StrategySimulation.Period invalid(
      String label,
      LocalDate start,
      LocalDate end,
      String reason,
      int completed,
      int skipped,
      double fees,
      boolean open) {
    return new StrategySimulation.Period(
        label, start, end, "INCOMPLETE", completed, skipped, null, null, null, fees, open, reason);
  }

  private static Double benchmark(TechnicalReplayInput input, LocalDate start, LocalDate end) {
    var bars =
        input.source().benchmark().stream()
            .filter(b -> !b.getTradeDate().isBefore(start) && !b.getTradeDate().isAfter(end))
            .sorted(java.util.Comparator.comparing(MarketBarDO::getTradeDate))
            .toList();
    var expected =
        input.source().tradingDates().stream()
            .filter(d -> !d.isBefore(start) && !d.isAfter(end))
            .sorted()
            .toList();
    if (expected.size() < 2
        || bars.size() != expected.size()
        || !bars.stream().map(MarketBarDO::getTradeDate).toList().equals(expected)) return null;
    if (bars.stream()
        .anyMatch(
            b ->
                !Boolean.FALSE.equals(b.getSynthetic())
                    || b.getFactor() == null
                    || b.getFactor().signum() <= 0
                    || b.getClose() == null
                    || b.getClose().signum() <= 0)) return null;
    if (bars.stream().map(MarketBarDO::getSource).distinct().count() != 1) return null;

    var first = bars.getFirst();
    var last = bars.getLast();
    if (first.getFactor() == null
        || last.getFactor() == null
        || first.getFactor().signum() <= 0
        || first.getClose() == null
        || last.getClose() == null
        || !java.util.Objects.equals(first.getSource(), last.getSource())) return null;
    return last.getClose().doubleValue()
            * last.getFactor().doubleValue()
            / (first.getClose().doubleValue() * first.getFactor().doubleValue())
        - 1;
  }
}
