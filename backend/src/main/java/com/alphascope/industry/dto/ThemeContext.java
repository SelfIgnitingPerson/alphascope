package com.alphascope.industry.dto;

import com.alphascope.industry.entity.ThemeScore;
import com.alphascope.market.dto.MarketBarDO;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 报告采用的完整主题样本证据，只由本次已保存的数据构建并冻结。
 *
 * @author chenxuefeng
 */
public record ThemeContext(
    /** 稳定主题代码。 */
    String code,
    /** 主题中文名称。 */
    String name,
    /** 研究时点可见的披露样本与原文信息。 */
    CohortDetailDO cohort,
    /** 本次样本成员及真实历史行情。 */
    List<Member> members,
    /** 已保存的同一主题评分。 */
    List<ThemeScore> scores,
    /** 实际交易日历要求的日期集合。 */
    Set<LocalDate> expectedDates) {
  /**
   * 本报告冻结的披露样本成员及其同源历史行情。
   *
   * @author chenxuefeng
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Member(
      /** 样本成员证券代码。 */
      String symbol,
      /** 样本成员中文名称。 */
      String name,
      /** 不可变样本主键。 */
      Long cohortId,
      /** 名单覆盖范围。 */
      String scope,
      /** 原始来源文档主键。 */
      Long sourceDocumentId,
      /** 可核查的原文 URL。 */
      String sourceUrl,
      /** 截至研究业务日的同源原价与因子。 */
      List<MarketBarDO> history) {}
}
