package com.alphascope.market.calculator;

import java.util.Map;
import java.util.Set;

/**
 * 五维研究模型。上游必须明确提供完整维度；缺一项则UNKNOWN，不按可用项放大。
 *
 * @author chenxuefeng
 */
public final class MarketScoreCalculator {
  private MarketScoreCalculator() {}

  /**
   * 一个全市场评分维度及其证据；缺分数时不参与放大或零值补齐。
   *
   * @author chenxuefeng
   */
  public record Dimension(
      /** 模型评分；缺失时保持空值，不能按0分或按已有维度放大。 */
      Double score,

      /** 本条判断采用的具体指标值或证据列表。 */
      Map<String, Object> evidence) {}

  /**
   * 全市场五维模型的评分、状态及各维证据。
   *
   * @author chenxuefeng
   */
  public record Result(
      /** 模型评分；缺失时保持空值，不能按0分或按已有维度放大。 */
      Double score,

      /** 模型输出的市场状态；缺少完整维度时必须为UNKNOWN。 */
      String state,

      /** 评分或观察模型标识，用于区分计算规则。 */
      String modelVersion,

      /** 按稳定维度代码组织的评分及原始证据。 */
      Map<String, Dimension> dimensions) {}

  public static Result calculate(Map<String, Dimension> dimensions) {
    var required =
        Set.of(
            "trend", "turnoverDirection", "breadth", "themeRelativeStrength", "leaderConfirmation");
    boolean complete = dimensions.keySet().containsAll(required);
    double total = 0;
    for (String key : required) {
      Dimension d = dimensions.get(key);
      if (d == null || d.score() == null) {
        complete = false;
        continue;
      }
      if (!Double.isFinite(d.score()) || d.score() < 0 || d.score() > 20) {
        throw new IllegalArgumentException("维度评分必须在0–20");
      }
      total += d.score();
    }
    if (!complete) {
      return new Result(null, "UNKNOWN", "MARKET_V1", dimensions);
    }
    return new Result(
        total,
        total >= 80
            ? "STRONG"
            : total >= 60
                ? "POSITIVE"
                : total >= 40 ? "SIDEWAYS" : total >= 20 ? "WEAK" : "RISK_OFF",
        "MARKET_V1",
        dimensions);
  }
}
