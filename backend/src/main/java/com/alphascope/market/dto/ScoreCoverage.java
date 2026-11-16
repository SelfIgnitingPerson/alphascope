package com.alphascope.market.dto;

/**
 * 评分输入的范围与完整性证明，不将固定样本冒充完整市场。
 *
 * @author chenxuefeng
 */
public record ScoreCoverage(
    /** 实际范围FULL_A、SH_SZ_A或DISCLOSED_RESEARCH_SAMPLE。 */
    String scope,
    /** 预期分母，必须大于0。 */
    int expected,
    /** 有效观测分子，不能超过分母。 */
    int observed,
    /** 上游对完整清单的明确核验。 */
    boolean rosterVerified) {
  /**
   * 核验分母和最低覆盖率。
   *
   * @param minimum 最低有效覆盖率
   * @return 分母有效且满足覆盖时为true
   */
  public boolean sufficient(double minimum) {
    return rosterVerified
        && expected > 0
        && observed >= 0
        && observed <= expected
        && observed / (double) expected >= minimum;
  }
}
