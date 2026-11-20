package com.alphascope.backtest.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 两月量价组合策略的保守模拟，不代表真实成交或有效性认证。
 *
 * @author chenxuefeng
 * @param version 固定执行假设版本
 * @param parameters 实际费率、资金和成交额参与门槛
 * @param periods 分别独立起仓的全区间、较早段和较晚段
 * @param limitations 尚未核实的可成交性与选择偏差
 */
public record StrategySimulation(
    String version,
    Map<String, Double> parameters,
    List<Period> periods,
    List<String> limitations,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Comparison> comparisons,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<StockMediumReplay> stockMediumReplays) {
  /** 旧版证据没有对照组，保持原冻结序列化口径。 */
  public StrategySimulation(
      String version,
      Map<String, Double> parameters,
      List<Period> periods,
      List<String> limitations) {
    this(version, parameters, periods, limitations, null, null);
  }

  /** 同等资金、成本和区间下的冻结策略对照。 @author chenxuefeng */
  public record Comparison(String code, String title, List<Period> periods) {}

  /** 每只证券以同一指定本金独立回放中期回踩方案和顺趋势候选。 @author chenxuefeng */
  public record StockMediumReplay(String symbol, List<Period> original, List<Period> trend) {}

  /**
   * 单个独立区间，不跨留出边界携带仓位。
   *
   * @author chenxuefeng
   * @param label ALL、DISCOVERY或HOLDOUT
   * @param start 区间起点
   * @param end 区间终点
   * @param status 完整回顾或具体不能计算原因
   * @param completedTrades 已完成卖出次数，非独立统计样本数
   * @param skippedOrders 被停牌、缺行情或保守涨跌停筛选跳过的委托
   * @param netReturn 扣费后净值收益，不合格时为空
   * @param maximumDrawdown 日收盘净值最大回撤，不合格时为空
   * @param benchmarkReturn 同区间基准收益，不合格时为空
   * @param fees 已实际计入的模拟费用
   * @param openPosition 区间末是否仍有持仓，未假定可强制平仓
   * @param reason 完整性和执行边界说明
   * @param tradeStatistics 从首次买入到完全卖出的扣费盈亏统计，旧版本及证据不足时为空
   * @param skipReasons 未成交原因计数，独立账户旧版本为空
   */
  public record Period(
      String label,
      LocalDate start,
      LocalDate end,
      String status,
      int completedTrades,
      int skippedOrders,
      Double netReturn,
      Double maximumDrawdown,
      Double benchmarkReturn,
      double fees,
      boolean openPosition,
      String reason,
      @JsonInclude(JsonInclude.Include.NON_NULL) TradeStatistics tradeStatistics,
      @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> skipReasons,
      @JsonInclude(JsonInclude.Include.NON_NULL) TradeQuality tradeQuality,
      @JsonInclude(JsonInclude.Include.NON_NULL) ReplayDetails details) {
    /** 旧版证据保存原有字段和顺序。 */
    public Period(
        String label,
        LocalDate start,
        LocalDate end,
        String status,
        int completedTrades,
        int skippedOrders,
        Double netReturn,
        Double maximumDrawdown,
        Double benchmarkReturn,
        double fees,
        boolean openPosition,
        String reason,
        TradeStatistics tradeStatistics,
        Map<String, Integer> skipReasons) {
      this(
          label,
          start,
          end,
          status,
          completedTrades,
          skippedOrders,
          netReturn,
          maximumDrawdown,
          benchmarkReturn,
          fees,
          openPosition,
          reason,
          tradeStatistics,
          skipReasons,
          null,
          null);
    }
  }

  /** 新版回放保存的完整平仓、成交和未成交记录。 @author chenxuefeng */
  public record ReplayDetails(
      List<ClosedCycle> closedCycles,
      List<OrderEvent> orders,
      int filteredEntries,
      @JsonInclude(JsonInclude.Include.NON_NULL) EntryExecution entryExecution) {}

  /** 全区间的建仓信号、成交和拦截计数，逐笔列表仅展示最近部分尝试。 @author chenxuefeng */
  public record EntryExecution(int signals, int filled, Map<String, Integer> blocked) {}

  /** 从首次建仓到完全退出的扣费净盈亏，已经计入核验过的公司行动。 @author chenxuefeng */
  public record ClosedCycle(String symbol, LocalDate openedOn, LocalDate closedOn, double netPnl) {}

  /** 一次成交或未成交尝试，保留收盘依据和开盘执行日期。 @author chenxuefeng */
  public record OrderEvent(
      String symbol,
      LocalDate signalDate,
      LocalDate executionDate,
      String action,
      String trigger,
      Long shares,
      Double fillPrice,
      Double fee,
      String blockedReason,
      @JsonInclude(JsonInclude.Include.NON_NULL) EntryBudget entryBudget) {
    /** 保留旧版逐笔记录的可复算字段。 */
    public OrderEvent(
        String symbol,
        LocalDate signalDate,
        LocalDate executionDate,
        String action,
        String trigger,
        Long shares,
        Double fillPrice,
        Double fee,
        String blockedReason) {
      this(
          symbol,
          signalDate,
          executionDate,
          action,
          trigger,
          shares,
          fillPrice,
          fee,
          blockedReason,
          null);
    }
  }

  /** 次日开盘时一手建仓的实际模拟预算，金额为人民币元。 @author chenxuefeng */
  public record EntryBudget(
      double oneLotAmount,
      double oneLotCost,
      double cash,
      double positionRoom,
      Double estimatedStopLoss,
      double riskLimit) {}

  /** 完整周期净盈亏和退出延迟，金额为元。 @author chenxuefeng */
  public record TradeQuality(
      Double averageClosedPnl,
      Double closedPnlWithoutBestWinner,
      int delayedExitSessions,
      int longestExitDelay,
      boolean pendingExit,
      List<MarketRegime> marketRegimes) {}

  /** 按入场前已知的基准趋势分组的完整平仓周期。 @author chenxuefeng */
  public record MarketRegime(String code, int closedTrades, int winningTrades, Double netPnl) {}

  /**
   * 每个从空仓到再次空仓的完整持仓周期只统计一次。
   *
   * @author chenxuefeng
   * @param closedTrades 完全平仓的周期数
   * @param winningTrades 扣除费用与滑点后净盈利的周期数
   * @param losingTrades 扣费后净亏损的周期数
   * @param breakEvenTrades 净盈亏按分计为零的周期数
   * @param winRate 盈利周期占完全平仓周期的比例，无样本时为空
   */
  public record TradeStatistics(
      int closedTrades, int winningTrades, int losingTrades, int breakEvenTrades, Double winRate) {}
}
