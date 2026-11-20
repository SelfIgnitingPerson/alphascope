package com.alphascope.backtest.calculator;

import static com.alphascope.backtest.calculator.StrategyReplayCalculator.SLIPPAGE;
import static com.alphascope.backtest.calculator.StrategyReplayCalculator.commission;
import static com.alphascope.backtest.calculator.StrategyReplayCalculator.sellFees;

import com.alphascope.backtest.dto.PortfolioReplaySettings;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.vo.StrategySimulation;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 按交易日使用同一本金执行多个证券的量价方案，持仓及风险预算逐日核对。
 *
 * @author chenxuefeng
 */
public final class PortfolioReplayCalculator {
  public static final String VERSION = "PRICE_VOLUME_SHARED_CASH_V4";
  public static final String JOURNAL_VERSION = "PRICE_VOLUME_SHARED_CASH_V3";
  public static final String PREVIOUS_VERSION = "PRICE_VOLUME_SHARED_CASH_V2";
  public static final String LEGACY_VERSION = "PRICE_VOLUME_SHARED_CASH_V1";
  private static final double RISK_LIMIT = .01;
  private static final double POSITION_LIMIT = .25;
  private static final double PARTICIPATION = .01;
  private static final int LOT = 100;

  private PortfolioReplayCalculator() {}

  /**
   * 冻结参数下逐日复算全区间与独立起仓的两段历史结果。
   *
   * @param settings 本金、起止日期及完整证券范围
   * @param inputs 每只证券已冻结的行情、日历和公司行动
   * @return 一个资金账户的模拟结果与成交限制
   */
  public static StrategySimulation evaluate(
      PortfolioReplaySettings settings, List<TechnicalReplayInput> inputs) {
    return evaluate(settings, inputs, VERSION);
  }

  /** 按记录保存的执行协议复算，旧结果仍使用旧规则。 */
  public static StrategySimulation evaluate(
      PortfolioReplaySettings settings, List<TechnicalReplayInput> inputs, String version) {
    if (!VERSION.equals(version)
        && !JOURNAL_VERSION.equals(version)
        && !PREVIOUS_VERSION.equals(version)
        && !LEGACY_VERSION.equals(version)) throw new IllegalArgumentException("不支持的组合执行协议");
    return evaluate(settings, inputs, version, null, !LEGACY_VERSION.equals(version));
  }

  /** 新单股报告使用同一套成交时点和资金账务，保留用户指定的区间划分。 */
  static StrategySimulation evaluateSingle(
      TechnicalReplayInput input, double capital, String version) {
    var request = input.source().request();
    return evaluate(
        new PortfolioReplaySettings(
            request.getStart(), request.getEnd(), capital, List.of(request.getSymbol())),
        List.of(input),
        version,
        request.getSplitDate(),
        false);
  }

  private static StrategySimulation evaluate(
      PortfolioReplaySettings settings,
      List<TechnicalReplayInput> inputs,
      String version,
      LocalDate splitDate,
      boolean compare) {
    boolean legacy = LEGACY_VERSION.equals(version);
    boolean detailed = VERSION.equals(version) || JOURNAL_VERSION.equals(version);
    var start = settings.start();
    var end = settings.end();
    var split =
        splitDate == null
            ? start.plusDays(Math.max(1, ChronoUnit.DAYS.between(start, end) * 7 / 10))
            : splitDate;
    var intervals = new ArrayList<>(List.of(new Interval("ALL", start, end)));
    if (split.isAfter(start) && !split.isAfter(end)) {
      intervals.add(new Interval("DISCOVERY", start, split.minusDays(1)));
      intervals.add(new Interval("HOLDOUT", split, end));
    }
    var sources = new TreeMap<String, TechnicalReplayInput>();
    for (var input : inputs) sources.put(input.source().request().getSymbol(), input);
    String problem = validateScope(settings, sources);
    var evidence = new SignalCache();
    var periods =
        evaluatePeriods(
            settings.capital(),
            intervals,
            sources,
            problem,
            ReplayRule.ORIGINAL,
            legacy,
            detailed,
            evidence);
    var comparisons =
        compare
            ? java.util.Arrays.stream(ReplayRule.values())
                .filter(rule -> rule != ReplayRule.ORIGINAL)
                .filter(
                    rule ->
                        detailed
                            || rule != ReplayRule.BUY_AND_HOLD
                                && rule != ReplayRule.ORIGINAL_VOLUME)
                .map(
                    rule ->
                        new StrategySimulation.Comparison(
                            rule.name(),
                            rule.title,
                            evaluatePeriods(
                                settings.capital(),
                                intervals,
                                sources,
                                problem,
                                rule,
                                false,
                                detailed && rule == ReplayRule.ORIGINAL_VOLUME,
                                evidence)))
                .toList()
            : null;
    var stockMediumReplays =
        VERSION.equals(version)
            ? settings.symbols().stream()
                .sorted()
                .map(
                    symbol -> {
                      Map<String, TechnicalReplayInput> single =
                          sources.containsKey(symbol)
                              ? Map.of(symbol, sources.get(symbol))
                              : Map.of();
                      return new StrategySimulation.StockMediumReplay(
                          symbol,
                          evaluatePeriods(
                              settings.capital(),
                              intervals,
                              single,
                              problem,
                              ReplayRule.MEDIUM_ORIGINAL,
                              false,
                              true,
                              evidence),
                          evaluatePeriods(
                              settings.capital(),
                              intervals,
                              single,
                              problem,
                              ReplayRule.MEDIUM_TREND,
                              false,
                              true,
                              evidence));
                    })
                .toList()
            : null;
    return new StrategySimulation(
        version,
        Map.of(
            "initialCapital",
            settings.capital(),
            "perPositionRisk",
            RISK_LIMIT,
            "maximumPositionWeight",
            POSITION_LIMIT,
            "commissionRate",
            StrategyReplayCalculator.COMMISSION,
            "stampDutySellRate",
            StrategyReplayCalculator.STAMP,
            "slippageRate",
            SLIPPAGE,
            "minimumCommission",
            5d,
            "lotSize",
            (double) LOT,
            "maximumAmountParticipation",
            PARTICIPATION),
        periods,
        legacy
            ? List.of(
                "全部证券共用可用现金，单股市值上限为账户权益的25%，单股至止损的估计风险预算为账户权益的1%，包含买卖成本。跳空可能使实际损失超过估计。",
                "收盘出信号，下一交易日开盘先卖后买；同向委托按证券代码排序。卖出后资金可用于同日买入。各区间独立空仓开始。",
                "最低佣金5元，整手100股。单价日、开盘相对参考价变动达4.5%或成交额限制会跳过委托；日线成交额仅作为模拟流动性限制。",
                "现金分红依据冻结公告计入，模拟预扣20%红利税。区间末持仓按收盘估值，胜率只统计完全平仓的周期。",
                "证券范围为当前选择的自选，结果基于历史行情版本和模拟成交。")
            : List.of(
                "收盘形成信号，下一交易日开盘先卖后买。买入容量按开盘前最多20个同源有效日的平均成交额估算；无集合竞价和委托簿证据。",
                "按开盘价与前收或已核验除权参考价作4.5%保守跳空筛选。未成交退出跨交易日保留，记录延迟；减仓可升级为退出。",
                "各组同资金、费用、滑点、单股25%仓位和1%止损估计风险预算。100股整手。区间末按收盘估值，胜率仅统计完全平仓周期。",
                "短期候选第20个市场交易日尝试退出，可成交限制会延迟。中期按6个月到期退出，逐项增补回踩、量能与原结构风险条件。",
                "行情分组使用入场前20个交易日的基准收益：达到±5%为上涨或下跌，其余为震荡；缺完整基准时单列。",
                "使用当前保存的历史版本。较晚区间独立空仓运行，表示时间分段回顾；当前样本有选择偏差，所有对照均为待验证候选。"),
        comparisons,
        stockMediumReplays);
  }

  private static List<StrategySimulation.Period> evaluatePeriods(
      double capital,
      List<Interval> intervals,
      Map<String, TechnicalReplayInput> sources,
      String problem,
      ReplayRule rule,
      boolean legacy,
      boolean detailed,
      SignalCache evidence) {
    return intervals.stream()
        .map(
            interval -> {
              var account = new Account(capital, interval, rule, legacy, detailed, evidence);
              return problem == null ? account.run(sources) : account.incomplete(problem);
            })
        .toList();
  }

  /** 单次回放各候选组共享的收盘计算，按标的、日期隔离。 @author chenxuefeng */
  private static final class SignalCache {
    private final Map<String, Map<LocalDate, ReplaySignals>> bySymbol = new java.util.HashMap<>();

    private ReplaySignals read(Position position, LocalDate date) {
      return bySymbol
          .computeIfAbsent(position.symbol, ignored -> new java.util.HashMap<>())
          .computeIfAbsent(date, day -> new ReplaySignals(day, position.prices, position.input));
    }
  }

  private static String validateScope(
      PortfolioReplaySettings settings, Map<String, TechnicalReplayInput> sources) {
    if (sources.isEmpty() || !sources.keySet().equals(new java.util.TreeSet<>(settings.symbols())))
      return "部分所选证券缺少可复算历史输入";
    var expected = replayDates(settings, sources.values().iterator().next());
    for (var input : sources.values()) {
      var req = input.source().request();
      if (!settings.start().equals(req.getStart())
          || !settings.end().equals(req.getEnd())
          || !expected.equals(replayDates(settings, input))) return "证券回放区间或交易日历不一致";
    }
    return null;
  }

  private static List<LocalDate> replayDates(
      PortfolioReplaySettings settings, TechnicalReplayInput input) {
    return input.source().tradingDates().stream()
        .filter(day -> !day.isBefore(settings.start()) && !day.isAfter(settings.end()))
        .sorted()
        .toList();
  }

  /** 独立空仓开始的区间。 @author chenxuefeng */
  private record Interval(String label, LocalDate start, LocalDate end) {}

  /** 共享现金和每只证券的实际持仓账务。 @author chenxuefeng */
  private static final class Account {
    private static final int RECENT_MEDIUM_ORDERS = 40;
    private final double capital;
    private final Interval interval;
    private final ReplayRule rule;
    private final boolean legacy;
    private final boolean buyAndHold;
    private final boolean detailed;
    private final SignalCache evidence;
    private final List<Position> positions = new ArrayList<>();
    private final NavigableMap<LocalDate, Double> receivables = new TreeMap<>();
    private final Map<String, Integer> skips = new TreeMap<>();
    private final Map<Regime, RegimeTally> regimes = new java.util.EnumMap<>(Regime.class);
    private final List<StrategySimulation.ClosedCycle> cycles = new ArrayList<>();
    private final List<StrategySimulation.OrderEvent> orders = new ArrayList<>();
    private final Map<String, Integer> blockedEntries = new TreeMap<>();
    private double cash, peak, drawdown, fees;
    private int sales, wins, losses, even, session, delayedExitSessions, longestExitDelay;
    private double closedPnl, bestWinner;
    private LocalDate executionDate, nextSession;
    private int filteredEntries, entrySignals, filledEntries;

    private Account(
        double capital,
        Interval interval,
        ReplayRule rule,
        boolean legacy,
        boolean detailed,
        SignalCache evidence) {
      this.capital = capital;
      this.interval = interval;
      this.rule = rule;
      this.legacy = legacy;
      this.buyAndHold = rule == ReplayRule.BUY_AND_HOLD;
      this.detailed = detailed;
      this.evidence = evidence;
      cash = capital;
      peak = capital;
    }

    private StrategySimulation.Period run(Map<String, TechnicalReplayInput> sources) {
      for (var input : sources.values()) {
        var position = new Position(input);
        if (position.duplicate) return incomplete(position.symbol + " 存在重复行情日期");
        positions.add(position);
      }
      var first = positions.getFirst().input;
      var dates =
          first.source().tradingDates().stream()
              .filter(d -> !d.isBefore(interval.start()) && !d.isAfter(interval.end()))
              .sorted()
              .toList();
      if (dates.size() < 2) return incomplete("权威交易日历不足");
      if (buyAndHold) {
        for (var position : positions) {
          var prior = position.prices.lowerEntry(dates.getFirst());
          if (prior != null && validBar(position, prior.getValue())) {
            position.lastClose = prior.getValue().getClose().doubleValue();
            position.factor = prior.getValue().getFactor().doubleValue();
          }
        }
      }
      for (var date : dates) {
        ExecutionContext.checkpoint();
        executionDate = date;
        session++;
        nextSession = session < dates.size() ? dates.get(session) : null;
        for (var due : new ArrayList<>(receivables.headMap(date, true).keySet()))
          cash += receivables.remove(due);
        for (var position : positions) {
          if (buyAndHold && position.shares == 0 && position.lastClose > 0) position.pending = 1;
          String issue = prepareDay(position, date);
          if (issue != null) return incomplete(position.symbol + " " + date + " " + issue);
        }
        for (var position : positions) if (position.pending < 0) execute(position);
        for (var position : positions) if (position.pending > 0) execute(position);
        for (var position : positions) evaluateClose(position, date);
        double equity = equity(false);
        peak = Math.max(peak, equity);
        drawdown = Math.max(drawdown, 1 - equity / peak);
      }
      executionDate = null;
      for (var position : positions)
        if (position.pending != 0) skip(position, position.pending, "区间末尚无下一交易日");
      if (buyAndHold) {
        long bought = positions.stream().filter(p -> p.openedDate != null).count();
        if (bought == 0) return incomplete("等额买入持有未能成交任何标的");
        return result(
            "ASSUMPTION_BASED_REPLAY",
            equity(false) / capital - 1,
            drawdown,
            null,
            "每只证券分配等额本金，首次可成交开盘买入，留存剩余现金，期末按收盘估值。实际买入 %d／%d 只。".formatted(bought, positions.size()),
            null);
      }
      int closed = wins + losses + even;
      return result(
          "ASSUMPTION_BASED_REPLAY",
          equity(false) / capital - 1,
          drawdown,
          StrategyReplayCalculator.benchmark(first, interval.start(), interval.end()),
          "使用共用资金按开盘模拟成交，末期持仓按收盘估值。",
          new StrategySimulation.TradeStatistics(
              closed, wins, losses, even, closed == 0 ? null : wins / (double) closed));
    }

    private String prepareDay(Position p, LocalDate date) {
      p.bar = p.prices.get(date);
      var bar = p.bar;
      if (bar == null) return p.input.stockTradingDates().contains(date) ? "应有交易日缺少行情" : null;
      if (!validBar(p, bar)) return "原价、因子、成交额或来源不合格";
      var actions =
          p.input.corporateActions() == null
              ? List.<com.alphascope.fundamental.dto.CorporateActionInput>of()
              : p.input.corporateActions().stream()
                  .filter(
                      a ->
                          date.equals(a.exDate())
                              && p.symbol.equals(a.symbol())
                              && p.source.equals(a.priceSource()))
                  .toList();
      double factor = bar.getFactor().doubleValue();
      boolean changed = p.factor > 0 && Math.abs(factor / p.factor - 1) > 1e-8;
      if (p.shares > 0 && (changed || !actions.isEmpty())) {
        if (actions.size() != 1) return "持仓期间缺少可靠公司行动账务";
        var action = actions.getFirst();
        if (Math.abs(action.factorBefore().doubleValue() / p.factor - 1) > 1e-6
            || Math.abs(action.factorAfter().doubleValue() / factor - 1) > 1e-6
            || action.paymentDate().isBefore(date)
            || action.shareMultiplier().compareTo(BigDecimal.ONE) > 0
                && !action.shareListingDate().equals(date)) return "公司行动日期或因子与冻结行情不一致";
        double shares = p.shares * action.shareMultiplier().doubleValue();
        if (Math.abs(shares - Math.rint(shares)) > 1e-6) return "公司行动缺少零股处置证据";
        double gross = p.shares * action.cashPerShare().doubleValue();
        p.tradeCashFlow += gross * .8;
        fees += gross * .2;
        if (action.paymentDate().equals(date)) cash += gross * .8;
        else receivables.merge(action.paymentDate(), gross * .8, Double::sum);
        p.shares = (long) Math.rint(shares);
        p.stop *= p.factor / factor;
        p.lastClose = action.exReferencePrice().doubleValue();
      } else if (p.shares == 0 && p.pending > 0 && changed) {
        if (actions.size() != 1
            || Math.abs(actions.getFirst().factorBefore().doubleValue() / p.factor - 1) > 1e-6
            || Math.abs(actions.getFirst().factorAfter().doubleValue() / factor - 1) > 1e-6) {
          skip(p, p.pending, "买入遇除权且参考依据不足");
          p.pending = 0;
        } else {
          p.stop *= p.factor / factor;
          p.lastClose = actions.getFirst().exReferencePrice().doubleValue();
        }
      }
      p.factor = factor;
      return null;
    }

    private boolean validBar(Position p, MarketBarDO bar) {
      return p.source.equals(bar.getSource())
          && Boolean.FALSE.equals(bar.getSynthetic())
          && positive(bar.getOpen())
          && positive(bar.getClose())
          && positive(bar.getFactor())
          && positive(bar.getAmount());
    }

    private void execute(Position p) {
      int order = p.pending;
      p.pending = 0;
      if (p.bar == null) {
        skip(p, order, "次日停牌或无可成交行情");
        deferSell(p, order);
      } else if (legacy
          ? StrategyReplayCalculator.openingRestricted(p.bar, p.lastClose)
          : ReplayOpening.restricted(p.bar.getOpen().doubleValue(), p.lastClose)) {
        skip(p, order, "次日开盘风控");
        deferSell(p, order);
      } else if (order > 0) buy(p);
      else if (sell(p, order == -2)) p.exitDelay = 0;
      else deferSell(p, order);
    }

    private void deferSell(Position p, int order) {
      if (legacy || order >= 0) return;
      p.pending = order;
      delayedExitSessions++;
      longestExitDelay = Math.max(longestExitDelay, ++p.exitDelay);
    }

    private double turnoverCapacity(Position p) {
      double amount =
          legacy ? p.bar.getAmount().doubleValue() : ReplayOpening.priorTurnover(p.prices, p.bar);
      return amount * PARTICIPATION;
    }

    private void buy(Position p) {
      double open = p.bar.getOpen().doubleValue();
      double fill = open * (1 + SLIPPAGE);
      double oneLot = LOT * fill;
      double equity = equity(true);
      double allocation = buyAndHold ? capital / positions.size() : cash;
      var limit =
          new BuyLimit(
              equity * RISK_LIMIT,
              Math.min(cash, allocation),
              buyAndHold ? allocation : equity * POSITION_LIMIT - p.shares * open,
              turnoverCapacity(p),
              fill,
              open,
              p.stop,
              p.shares,
              !buyAndHold);
      String reason = limit.exclusion(1);
      var budget = detailed && rule.medium() && p.shares == 0 ? limit.oneLotBudget() : null;
      if (reason != null) {
        skips.merge(reason, 1, Integer::sum);
        recordOrder(p, 1, null, null, null, reason, budget);
        return;
      }
      long low = 1,
          high = (long) (Math.min(limit.cash(), Math.min(limit.room(), limit.turnover())) / oneLot);
      while (low < high) {
        long mid = low + (high - low + 1) / 2;
        if (limit.exclusion(mid) == null) low = mid;
        else high = mid - 1;
      }
      long qty = low * LOT;
      double amount = qty * fill;
      double fee = commission(amount);
      recordOrder(p, 1, qty, fill, fee, null, budget);
      cash -= amount + fee;
      p.tradeCashFlow -= amount + fee;
      fees += fee;
      if (p.shares == 0) {
        p.entrySession = session;
        p.openedDate = p.bar.getTradeDate();
        if (!legacy) p.entryRegime = entryRegime(p.input, p.bar.getTradeDate());
      }
      p.shares += qty;
    }

    private boolean sell(Position p, boolean exit) {
      long qty = exit ? p.shares : p.shares / (2 * LOT) * LOT;
      if (qty == 0) qty = p.shares;
      double open = p.bar.getOpen().doubleValue();
      double fill = open * (1 - SLIPPAGE);
      double amount = qty * open * (1 - SLIPPAGE);
      if (amount > turnoverCapacity(p)) {
        skip(p, exit ? -2 : -1, "卖出超过成交额参与上限");
        return false;
      }
      double fee = sellFees(amount);
      recordOrder(p, exit ? -2 : -1, qty, fill, fee, null);
      cash += amount - fee;
      p.tradeCashFlow += amount - fee;
      fees += fee;
      sales++;
      p.shares -= qty;
      if (p.shares != 0) return true;
      if (detailed)
        cycles.add(
            new StrategySimulation.ClosedCycle(
                p.symbol, p.openedDate, executionDate, p.tradeCashFlow));
      closedPnl += p.tradeCashFlow;
      bestWinner = Math.max(bestWinner, p.tradeCashFlow);
      int sign = BigDecimal.valueOf(p.tradeCashFlow).setScale(2, RoundingMode.HALF_UP).signum();
      if (sign > 0) wins++;
      else if (sign < 0) losses++;
      else even++;
      if (!legacy) {
        var tally = regimes.computeIfAbsent(p.entryRegime, ignored -> new RegimeTally());
        tally.closed++;
        if (sign > 0) tally.wins++;
        tally.pnl += p.tradeCashFlow;
      }
      p.stop = 0;
      p.tradeCashFlow = 0;
      return true;
    }

    private void evaluateClose(Position p, LocalDate date) {
      if (p.bar != null) p.lastClose = p.bar.getClose().doubleValue();
      if (buyAndHold) return;
      if (p.pending == -2) return;
      boolean shortExpiry =
          rule.maximumSessions > 0 && session - p.entrySession + 1 >= rule.maximumSessions - 1;
      boolean mediumExpiry =
          rule.medium()
              && p.openedDate != null
              && nextSession != null
              && !nextSession.isBefore(p.openedDate.plusMonths(6));
      if (p.shares > 0 && (shortExpiry || mediumExpiry)) {
        setOrder(p, date, -2, shortExpiry ? "短期持有到期" : "中期持有到期");
        return;
      }
      if (p.bar == null) return;
      var signals = legacy ? null : evidence.read(p, date);
      if (rule.medium()) {
        var order = signals.medium(rule, p.shares > 0);
        if (order.direction() < 0 || p.pending == 0) {
          // 保留同一待执行减仓的来源日期，退出升级时记录新的信号。
          if (order.direction() != p.pending)
            setOrder(p, date, order.direction(), mediumTrigger(p, order.direction()));
        }
        if (p.shares == 0 && order.direction() > 0) {
          p.stop = order.stop();
          if (detailed) entrySignals++;
        }
        return;
      }
      var study =
          legacy ? StrategyReplayCalculator.study(date, p.prices, p.input) : signals.study();
      if (p.shares > 0) {
        if (p.lastClose <= p.stop) setOrder(p, date, -2, "收盘触及建仓时的风险线");
        else if ("BREACHED".equals(study.stopStatus())) setOrder(p, date, -2, "收盘触及当日结构止损");
        else if (p.pending == 0 && matched(study, "REDUCE")) setOrder(p, date, -1, "量价减仓条件满足");
        else if (p.pending == 0 && matched(study, "ADD_RETEST")) setOrder(p, date, 1, "突破后回踩确认加仓");
      } else if (study.stopReference() != null && entryMatches(study)) {
        String trigger = matched(study, "OPEN_BREAKOUT") ? "放量突破建仓" : "回踩企稳建仓";
        if (rule == ReplayRule.ORIGINAL_VOLUME
            && (study.volumeRatio5() == null || study.volumeRatio5() < 1)) {
          filteredEntries++;
          orders.add(
              new StrategySimulation.OrderEvent(
                  p.symbol,
                  date,
                  null,
                  "建仓",
                  trigger,
                  null,
                  null,
                  null,
                  study.volumeRatio5() == null ? "缺少5日成交额量比" : "5日成交额量比低于1.00"));
          return;
        }
        setOrder(p, date, 1, trigger);
        p.stop = study.stopReference();
      }
    }

    private String mediumTrigger(Position p, int direction) {
      return switch (direction) {
        case -2 -> "中期趋势或结构失效";
        case -1 -> "中期遇压减仓";
        case 1 ->
            p.shares > 0
                ? "中期回踩确认加仓"
                : rule == ReplayRule.MEDIUM_TREND ? "MA60顺趋势条件满足" : "中期回踩条件满足";
        default -> null;
      };
    }

    private void setOrder(Position p, LocalDate date, int direction, String trigger) {
      p.pending = direction;
      if (detailed) {
        p.signalDate = date;
        p.trigger = trigger;
      }
    }

    private boolean entryMatches(PriceVolumeStudy study) {
      return switch (rule) {
        case SHORT_PULLBACK -> matched(study, "OPEN_PULLBACK");
        case SHORT_BREAKOUT -> matched(study, "OPEN_BREAKOUT");
        case SHORT_BASELINE ->
            "AVAILABLE".equals(study.status())
                && study.ma20() != null
                && study.close() != null
                && study.close() >= study.ma20();
        default -> matched(study, "OPEN_");
      };
    }

    private double equity(boolean atOpen) {
      double value = cash + receivables.values().stream().mapToDouble(Double::doubleValue).sum();
      for (var p : positions)
        value += p.shares * (atOpen && p.bar != null ? p.bar.getOpen().doubleValue() : p.lastClose);
      return value;
    }

    private void skip(Position p, int direction, String reason) {
      skips.merge(reason, 1, Integer::sum);
      recordOrder(p, direction, null, null, null, reason);
    }

    private void recordOrder(
        Position p, int direction, Long shares, Double fill, Double fee, String blocked) {
      recordOrder(p, direction, shares, fill, fee, blocked, null);
    }

    private void recordOrder(
        Position p,
        int direction,
        Long shares,
        Double fill,
        Double fee,
        String blocked,
        StrategySimulation.EntryBudget budget) {
      if (!detailed) return;
      if (rule.medium() && direction > 0 && p.shares == 0) {
        if (blocked == null) filledEntries++;
        else blockedEntries.merge(blocked, 1, Integer::sum);
      }
      String action =
          switch (direction) {
            case -2 -> "退出";
            case -1 -> "减仓";
            default -> p.shares == 0 ? "建仓" : "加仓";
          };
      orders.add(
          new StrategySimulation.OrderEvent(
              p.symbol,
              p.signalDate,
              executionDate,
              action,
              p.trigger,
              shares,
              fill,
              fee,
              blocked,
              budget));
    }

    private StrategySimulation.Period incomplete(String reason) {
      return result("INCOMPLETE", null, null, null, reason, null);
    }

    private StrategySimulation.Period result(
        String status,
        Double net,
        Double dd,
        Double benchmark,
        String reason,
        StrategySimulation.TradeStatistics statistics) {
      int skipped = skips.values().stream().mapToInt(Integer::intValue).sum();
      return new StrategySimulation.Period(
          interval.label(),
          interval.start(),
          interval.end(),
          status,
          sales,
          skipped,
          net,
          dd,
          benchmark,
          fees,
          positions.stream().anyMatch(p -> p.shares > 0),
          reason,
          statistics,
          Map.copyOf(skips),
          legacy || statistics == null
              ? null
              : new StrategySimulation.TradeQuality(
                  statistics.closedTrades() == 0 ? null : closedPnl / statistics.closedTrades(),
                  statistics.closedTrades() == 0 ? null : closedPnl - bestWinner,
                  delayedExitSessions,
                  longestExitDelay,
                  positions.stream().anyMatch(p -> p.pending < 0),
                  java.util.Arrays.stream(Regime.values())
                      .map(
                          code -> {
                            var tally = regimes.get(code);
                            return tally == null
                                ? new StrategySimulation.MarketRegime(code.name(), 0, 0, null)
                                : new StrategySimulation.MarketRegime(
                                    code.name(), tally.closed, tally.wins, tally.pnl);
                          })
                      .toList()),
          detailed && statistics != null
              ? new StrategySimulation.ReplayDetails(
                  List.copyOf(cycles),
                  rule.medium()
                      ? List.copyOf(
                          orders.subList(
                              Math.max(0, orders.size() - RECENT_MEDIUM_ORDERS), orders.size()))
                      : List.copyOf(orders),
                  filteredEntries,
                  rule.medium()
                      ? new StrategySimulation.EntryExecution(
                          entrySignals, filledEntries, Map.copyOf(blockedEntries))
                      : null)
              : null);
    }
  }

  /** 入场日前的基准走势分组。 @author chenxuefeng */
  private enum Regime {
    UP,
    DOWN,
    SIDEWAYS,
    UNKNOWN
  }

  private static Regime entryRegime(TechnicalReplayInput input, LocalDate openingDate) {
    var prior =
        input.source().tradingDates().stream()
            .filter(d -> d.isBefore(openingDate))
            .sorted(java.util.Comparator.reverseOrder())
            .limit(21)
            .toList();
    if (prior.size() != 21) return Regime.UNKNOWN;
    var move = StrategyReplayCalculator.benchmark(input, prior.getLast(), prior.getFirst());
    if (move == null) return Regime.UNKNOWN;
    if (move >= .05) return Regime.UP;
    return move <= -.05 ? Regime.DOWN : Regime.SIDEWAYS;
  }

  /** 累计同一市场状态的完整平仓收益。 @author chenxuefeng */
  private static final class RegimeTally {
    private int closed, wins;
    private double pnl;
  }

  private static boolean matched(PriceVolumeStudy study, String action) {
    return study.strategies() != null
        && study.strategies().stream()
            .anyMatch(
                s ->
                    ("OPEN_".equals(action) ? s.code().startsWith(action) : s.code().equals(action))
                        && "CONDITIONAL_MATCH".equals(s.status()));
  }

  private static boolean positive(BigDecimal value) {
    return value != null && Double.isFinite(value.doubleValue()) && value.signum() > 0;
  }

  /** 一只证券的持仓、行情索引及待执行委托。 @author chenxuefeng */
  private static final class Position {
    private final TechnicalReplayInput input;
    private final String symbol, source;
    private final NavigableMap<LocalDate, MarketBarDO> prices = new TreeMap<>();
    private final boolean duplicate;
    private MarketBarDO bar;
    private long shares;
    private int pending, entrySession, exitDelay;
    private Regime entryRegime;
    private LocalDate openedDate, signalDate;
    private String trigger;
    private double stop, lastClose, factor, tradeCashFlow;

    private Position(TechnicalReplayInput input) {
      this.input = input;
      symbol = input.source().request().getSymbol();
      boolean hasDuplicate = false;
      for (var value : input.source().bars())
        if (prices.put(value.getTradeDate(), value) != null) hasDuplicate = true;
      duplicate = hasDuplicate;
      source =
          prices.isEmpty() || prices.firstEntry().getValue().getSource() == null
              ? ""
              : prices.firstEntry().getValue().getSource();
    }
  }

  /** 开盘时可核对的一手成本及单股风险上限。 @author chenxuefeng */
  private record BuyLimit(
      double risk,
      double cash,
      double room,
      double turnover,
      double fill,
      double open,
      double stop,
      long held,
      boolean riskManaged) {
    private String exclusion(long lots) {
      double qty = lots * (double) LOT;
      double cost = qty * fill;
      if (cost + commission(cost) > cash) return "可用现金不足一手及费用";
      if (cost > room) return "单股仓位上限";
      if (cost > turnover) return "买入超过成交额参与上限";
      if (!riskManaged) return null;
      if (stop <= 0 || open <= stop) return "开盘已触及止损或参考无效";
      return estimatedLoss(qty, cost) > risk ? "一手超出单股风险预算" : null;
    }

    private double estimatedLoss(double qty, double cost) {
      double exit = (held + qty) * stop * (1 - SLIPPAGE);
      return held * open + cost + commission(cost) - exit + sellFees(exit);
    }

    private StrategySimulation.EntryBudget oneLotBudget() {
      double amount = LOT * fill;
      return new StrategySimulation.EntryBudget(
          amount,
          amount + commission(amount),
          cash,
          room,
          stop > 0 && open > stop ? estimatedLoss(LOT, amount) : null,
          risk);
    }
  }
}
