package com.alphascope.backtest.calculator;

/**
 * 已冻结的回放候选。参数或触发条件变化时增加执行协议版本。
 *
 * @author chenxuefeng
 */
enum ReplayRule {
  ORIGINAL("原量价规则", 0),
  SHORT_20("两种短期建仓 · 20日", 20),
  SHORT_PULLBACK("短期回踩建仓 · 20日", 20),
  SHORT_BREAKOUT("短期放量突破 · 20日", 20),
  SHORT_BASELINE("短期对照 · 收盘站上MA20 · 20日", 20),
  MEDIUM_ORIGINAL("现有中期规则", 0),
  MEDIUM_TREND("中期对照 · MA60趋势", 0),
  MEDIUM_RETEST("MA60趋势 + 回踩区间", 0),
  MEDIUM_VOLUME("MA60趋势 + 回踩 + 量能", 0),
  MEDIUM_RISK("MA60趋势 + 回踩 + 量能 + 原结构风险", 0),
  BUY_AND_HOLD("同股等额买入持有", 0),
  ORIGINAL_VOLUME("原量价规则 + 5日成交额确认", 0);

  final String title;
  final int maximumSessions;

  ReplayRule(String title, int maximumSessions) {
    this.title = title;
    this.maximumSessions = maximumSessions;
  }

  boolean medium() {
    return ordinal() >= MEDIUM_ORIGINAL.ordinal() && ordinal() <= MEDIUM_RISK.ordinal();
  }
}
