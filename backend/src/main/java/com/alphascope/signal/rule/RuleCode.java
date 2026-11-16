package com.alphascope.signal.rule;

/**
 * 当前完整风险规则清单，计算和产物核验共享稳定代码。
 *
 * @author chenxuefeng
 */
public enum RuleCode {
  TREND_BREAK_20,
  TREND_BREAK_60,
  VOLUME_SELL,
  NEW_LOW_20,
  RELATIVE_WEAK,
  PROFIT_SLOWDOWN,
  MARGIN_DECLINE,
  CASHFLOW_WARNING,
  POSITION_CONCENTRATION,
  INDUSTRY_DIVERGENCE
}
