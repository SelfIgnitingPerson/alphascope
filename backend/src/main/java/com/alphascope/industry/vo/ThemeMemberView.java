package com.alphascope.industry.vo;

import com.alphascope.industry.dto.ThemeMemberDO;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 对外查询响应；与数据库实体分离，保持单位和业务时点语义。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ThemeMemberView(
    /** 成员证券代码。 */
    String symbol,

    /** 成员中文名称。 */
    String name,

    /** 披露研究样本ID；人工归属时为0。 */
    Long cohortId,

    /** 成员名单的覆盖范围。 */
    String scope,

    /** 支持披露样本的来源ID。 */
    Long sourceDocumentId,

    /** 原文链接。 */
    String sourceUrl) {
  /**
   * 显式转换当前有效的样本成员；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 当前有效的样本成员，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static ThemeMemberView from(ThemeMemberDO row) {
    return new ThemeMemberView(
        row.getSymbol(),
        row.getName(),
        row.getCohortId(),
        row.getScope(),
        row.getSourceDocumentId(),
        row.getSourceUrl());
  }
}
