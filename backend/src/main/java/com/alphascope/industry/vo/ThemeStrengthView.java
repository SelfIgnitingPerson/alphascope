package com.alphascope.industry.vo;

import java.util.List;

/**
 * 主题强弱、有效成员及公开披露边界。
 *
 * @author chenxuefeng
 */
public record ThemeStrengthView(
    /** 已保存的历史主题评分。 */
    List<ThemeScoreView> items,

    /** 查询截止日期真实有效的研究样本成员。 */
    List<ThemeMemberView> members,

    /** 等权收益计算算法标识。 */
    String algorithmVersion,

    /** 样本研究范围，不能当成完整行业。 */
    String scope,

    /** 公开披露样本定义；人工样本时为空。 */
    CohortView cohort,

    /** 完整行业覆盖率，未核实保持 UNKNOWN。 */
    String industryCoverage) {}
