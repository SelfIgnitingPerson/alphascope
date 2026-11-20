package com.alphascope.common.task;

/**
 * 单一研究流水线的阶段标识，配置、持久化和日志共用。
 *
 * @author chenxuefeng
 */
public enum PipelineStage {
  FORECASTS,
  CALENDAR,
  THEME_MEMBERS,
  MARKET_SYNC,
  DATA_QUALITY,
  RESEARCH_DATA,
  MARKET_UNIVERSE,
  FULL_MARKET_ANALYSIS,
  MARKET_OBSERVATION,
  INDICATORS,
  SCORE_RULES,
  PLAN_REVIEW,
  REPORT,
  NOTIFICATION;

  /**
   * 检查配置中的阶段名称，避免拼写错误被忽略。
   *
   * @param name 原始阶段代码
   * @return 与某个阶段代码完全一致时为true
   */
  public static boolean known(String name) {
    for (PipelineStage stage : values()) {
      if (stage.name().equals(name)) {
        return true;
      }
    }
    return false;
  }
}
