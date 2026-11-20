package com.alphascope.backtest.calculator;

import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.vo.StrategySimulation;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * 使用生产两月量价条件，收盘决定、次个市场交易日开盘尝试执行；不优化参数。
 *
 * @author chenxuefeng
 */
public final class StrategyReplayCalculator {
  public static final String VERSION = "PRICE_VOLUME_EXECUTION_V4";
  public static final String PREVIOUS_VERSION = "PRICE_VOLUME_EXECUTION_V3";
  static final String CORPORATE_ACTION_VERSION = "PRICE_VOLUME_EXECUTION_V2";
  public static final String LEGACY_VERSION = "PRICE_VOLUME_EXECUTION_V1";
  private static final double CAPITAL = 100000;
  static final double COMMISSION = 0.0003, STAMP = 0.0005, SLIPPAGE = 0.0005;

  private StrategyReplayCalculator() {}

  /**
   * 从冻结原价、因子与日历回顾真实生产条件，区间不足不报告收益。
   *
   * @param input 已冻结的技术验证输入
   * @return 三个区间及不可省略的执行假设
   */
  public static StrategySimulation evaluate(TechnicalReplayInput input) {
    return evaluate(input, VERSION);
  }

  /**
   * 按冻结版本复算，保留旧档案的规则与指纹。
   *
   * @param input 已冻结的技术验证输入
   * @param version 执行规则版本
   * @return 指定版本的回放
   */
  public static StrategySimulation evaluate(TechnicalReplayInput input, String version) {
    if (VERSION.equals(version))
      return PortfolioReplayCalculator.evaluateSingle(input, CAPITAL, version);
    boolean current = PREVIOUS_VERSION.equals(version);
    String archivedVersion =
        input.corporateActions() == null ? LEGACY_VERSION : CORPORATE_ACTION_VERSION;
    if (!current && !archivedVersion.equals(version))
      throw new IllegalArgumentException("不支持的策略执行扩展版本");
    var request = input.source().request();
    var start = request.getStart();
    var end = request.getEnd();
    LocalDate split =
        request.getSplitDate() == null
            ? start.plusDays(Math.max(1, ChronoUnit.DAYS.between(start, end) * 7 / 10))
            : request.getSplitDate();
    var periods = new ArrayList<StrategySimulation.Period>();
    periods.add(period(input, new ReplayPeriod("ALL", start, end), current));
    if (split.isAfter(start) && !split.isAfter(end)) {
      periods.add(period(input, new ReplayPeriod("DISCOVERY", start, split.minusDays(1)), current));
      periods.add(period(input, new ReplayPeriod("HOLDOUT", split, end), current));
    }
    var limitations =
        new ArrayList<>(
            List.of(
                "当前价格版本回顾，非严格历史时点；当前自选有选择与幸存者偏差。",
                "建仓25%、加仓25%、减仓50%、风险线触及后下一交易日退出；这些固定参数不是个人仓位建议，也未优化。",
                "单价日、开盘相对前收涨跌达到4.5%的日期保守拒绝执行；没有历史ST及精确涨跌停资料，不能证明所有其他日期可成交。",
                "成交额参与上限1%、100股整手、最低佣金5元；滑点为假设，不是真实逐笔成交。",
                input.corporateActions() == null
                    ? "持仓中发生复权因子变化时区间标为公司行动证据不足，不猜测分红或送转账务。"
                    : "公司行动须有公告、同源前后因子、除权参考价及到账日；现金分红按20%预扣税保守计提，到账前记应收但不能用于买入。不支持配股、提前交易未上市股份或猜测零股处置。",
                "较晚区间独立起仓；有限样本和回顾结果不构成有效性或收益承诺。"));
    if (current)
      limitations.add("胜率按从首次买入到完全退出的持仓周期计算。分批买卖、手续费、卖出印花税、滑点及已核验权益均计入周期净盈亏。未平仓及未成交委托不计入胜率。");
    return new StrategySimulation(
        version,
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
        List.copyOf(limitations));
  }

  private static StrategySimulation.Period period(
      TechnicalReplayInput input, ReplayPeriod interval, boolean current) {
    var label = interval.label();
    var start = interval.start();
    var end = interval.end();
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
    double tradeCashFlow = 0;
    int winning = 0, losing = 0, breakEven = 0;
    Set<LocalDate> stockDates = input.stockTradingDates();
    var receivables = new TreeMap<LocalDate, Double>();
    for (var date : days) {
      ExecutionContext.checkpoint();
      var paid = new ArrayList<>(receivables.headMap(date, true).keySet());
      for (var due : paid) cash += receivables.remove(due);
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
      var actions =
          input.corporateActions() == null
              ? java.util.List.<com.alphascope.fundamental.dto.CorporateActionInput>of()
              : input.corporateActions().stream()
                  .filter(
                      a ->
                          a.exDate().equals(date)
                              && a.symbol().equals(input.source().request().getSymbol())
                              && sourceMatches(a.priceSource(), bar.getSource()))
                  .toList();
      if (shares > 0 && (Math.abs(factor / entryFactor - 1) > 0.00000001 || !actions.isEmpty())) {
        if (actions.size() != 1)
          return invalid(
              label, start, end, "持仓期间复权因子改变，缺少可靠公司行动账务", completed, skipped, fees, true);
        var action = actions.getFirst();
        if (Math.abs(action.factorBefore().doubleValue() / entryFactor - 1) > 0.000001
            || Math.abs(action.factorAfter().doubleValue() / factor - 1) > 0.000001
            || action.paymentDate().isBefore(date)
            || (action.shareMultiplier().compareTo(java.math.BigDecimal.ONE) > 0
                && !action.shareListingDate().equals(date)))
          return invalid(label, start, end, "公司行动日期或因子与冻结行情不一致", completed, skipped, fees, true);
        double nextShares = shares * action.shareMultiplier().doubleValue();
        if (Math.abs(nextShares - Math.rint(nextShares)) > 0.000001)
          return invalid(label, start, end, "公司行动产生零股，缺少可靠零股处置证据", completed, skipped, fees, true);
        double dividend = shares * action.cashPerShare().doubleValue() * .8;
        tradeCashFlow += dividend;
        fees += shares * action.cashPerShare().doubleValue() * .2;
        if (action.paymentDate().equals(date)) cash += dividend;
        else receivables.merge(action.paymentDate(), dividend, Double::sum);
        shares = Math.rint(nextShares);
        if (stop > 0) stop *= entryFactor / factor;
        if (pendingStop > 0) pendingStop *= entryFactor / factor;
        entryFactor = factor;
        lastClose = action.exReferencePrice().doubleValue();
      }
      if (pending != 0) {
        boolean locked = openingRestricted(bar, lastClose);
        if (locked) {
          skipped++;
        } else if (pending > 0) {
          double buy = open * (1 + SLIPPAGE);
          double budget =
              Math.min(Math.min(CAPITAL * 0.25, cash - 5), bar.getAmount().doubleValue() * 0.01);
          double quantity = Math.floor(Math.max(0, budget) / (buy * (1 + COMMISSION)) / 100) * 100;
          double commission = commission(quantity * buy);
          if (quantity > 0 && quantity * buy + commission <= cash) {
            cash -= quantity * buy + commission;
            tradeCashFlow -= quantity * buy + commission;
            shares += quantity;
            fees += commission;
            entryFactor = factor;
            if (stop <= 0) stop = pendingStop;
          } else skipped++;
        } else if (shares > 0) {
          double quantity = pending == -2 ? shares : Math.floor(shares / 2 / 100) * 100;
          if (quantity == 0) quantity = shares;
          double sell = open * (1 - SLIPPAGE);
          double commission = sellFees(quantity * sell);
          if (quantity * sell <= bar.getAmount().doubleValue() * 0.01) {
            cash += quantity * sell - commission;
            tradeCashFlow += quantity * sell - commission;
            shares -= quantity;
            fees += commission;
            completed++;
            if (shares == 0) {
              stop = 0;
              int outcome =
                  BigDecimal.valueOf(tradeCashFlow).setScale(2, RoundingMode.HALF_UP).signum();
              if (outcome > 0) winning++;
              else if (outcome < 0) losing++;
              else breakEven++;
              tradeCashFlow = 0;
            }
          } else skipped++;
        }
        pending = 0;
      }
      double equity =
          cash
              + shares * close
              + receivables.values().stream().mapToDouble(Double::doubleValue).sum();
      peak = Math.max(peak, equity);
      drawdown = Math.max(drawdown, 1 - equity / peak);
      var study = study(date, prices, input);
      if (shares > 0
          && (stop > 0 && close <= stop || current && "BREACHED".equals(study.stopStatus())))
        pending = -2;
      else if (study.strategies() != null) {
        if (shares > 0
            && study.strategies().stream()
                .anyMatch(
                    s ->
                        s.code().equals("REDUCE")
                            && s.status().equals(current ? "CONDITIONAL_MATCH" : "WATCH")))
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
    double equity =
        cash
            + shares * lastClose
            + receivables.values().stream().mapToDouble(Double::doubleValue).sum();
    int closed = winning + losing + breakEven;
    var statistics =
        current
            ? new StrategySimulation.TradeStatistics(
                closed, winning, losing, breakEven, closed == 0 ? null : winning / (double) closed)
            : null;
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
        "收盘决策、下一交易日开盘；末日挂单未假定成交，末仓按收盘估值。涨跌停与ST历史尚未独立核验。",
        statistics,
        null);
  }

  static com.alphascope.intelligence.vo.PriceVolumeStudy study(
      LocalDate date, NavigableMap<LocalDate, MarketBarDO> prices, TechnicalReplayInput input) {
    var start = date.minusMonths(2);
    var window = List.copyOf(prices.subMap(start, true, date, true).values());
    var expected =
        input.stockTradingDates().stream()
            .filter(d -> !d.isBefore(start) && !d.isAfter(date))
            .collect(java.util.stream.Collectors.toSet());
    String symbol = input.source().request().getSymbol();
    return PriceVolumeCalculator.calculate(
        PriceVolumeWindow.builder()
            .symbol(symbol)
            .name(symbol)
            .date(date)
            .bars(window)
            .expectedDates(expected)
            .synthetic(false)
            .suspended(false)
            .build());
  }

  static boolean openingRestricted(MarketBarDO bar, double reference) {
    return bar.getHigh() == null
        || bar.getLow() == null
        || bar.getHigh().compareTo(bar.getLow()) == 0
        || reference > 0 && Math.abs(bar.getOpen().doubleValue() / reference - 1) >= .045;
  }

  static double commission(double amount) {
    return Math.max(5, amount * COMMISSION);
  }

  static double sellFees(double amount) {
    return commission(amount) + amount * STAMP;
  }

  /**
   * 独立起仓的回顾日期与所处区间。
   *
   * @author chenxuefeng
   */
  private record ReplayPeriod(String label, LocalDate start, LocalDate end) {}

  private static boolean sourceMatches(String actual, String expected) {
    return java.util.Objects.equals(actual, expected);
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
        label,
        start,
        end,
        "INCOMPLETE",
        completed,
        skipped,
        null,
        null,
        null,
        fees,
        open,
        reason,
        null,
        null);
  }

  static Double benchmark(TechnicalReplayInput input, LocalDate start, LocalDate end) {
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
