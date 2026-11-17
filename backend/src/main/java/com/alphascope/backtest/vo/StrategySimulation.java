package com.alphascope.backtest.vo;

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
    List<String> limitations) {
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
      String reason) {}
}
