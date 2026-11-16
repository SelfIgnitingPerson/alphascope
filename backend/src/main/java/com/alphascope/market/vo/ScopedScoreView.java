package com.alphascope.market.vo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 有明确公式和范围的实验性五维研究评分，缺项不放大、不承诺收益。
 *
 * @author chenxuefeng
 */
public record ScopedScoreView(
    /** 当前规则方法。 */
    String modelVersion,
    /** 目标范围，当前页面固定FULL_A。 */
    String scope,
    /** 业务日期。 */
    LocalDate businessDate,
    /** 只有全部必需输入同范围合格时才有0到100总分。 */
    Double score,
    /** 完整时评级，否则UNKNOWN。 */
    String state,
    /** 研究规则尚未完成历史效果验证。 */
    String validationStatus,
    /** 各分项的原始分数、范围及准入结果。 */
    Map<String, Dimension> dimensions,
    /** 实际采用参数。 */
    Map<String, Double> parameters,
    /** 范围、代理与方法局限。 */
    List<String> limitations) {
  /**
   * 一项完整定义的维度，代理分数只能在原范围展示。
   *
   * @author chenxuefeng
   */
  public record Dimension(
      /** 原始分项分数0到20，证据不足为null。 */
      Double score,
      /** AVAILABLE同范围、PROXY代理范围、INSUFFICIENT_DATA缺证据。 */
      String status,
      /** 实际范围。 */
      String sourceScope,
      /** 可核对的计分公式。 */
      String formula,
      /** 数值证据与具体缺项。 */
      Map<String, Object> evidence) {}
}
