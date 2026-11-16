package com.alphascope.industry.vo;

import com.alphascope.industry.dto.CohortDetailDO;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公开来源联查响应，保留文档链接和披露时点。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CohortView(
    /** 记录主键，自增生成 */
    Long id,

    /** 主题主键，关联theme.id */
    Long themeId,

    /** 不可变研究样本唯一代码；来源或成员改变须新建样本标识 */
    String code,

    /** 样本覆盖范围标识，当前为DISCLOSED_RESEARCH_SAMPLE披露研究样本 */
    String scope,

    /** 研究样本中文名称 */
    String title,

    /** 来源文档主键，关联source_document.id */
    Long sourceDocumentId,

    /** 该样本公开依据最早可用于研究的时刻，上海时区 */
    LocalDateTime availableAt,

    /** 归属或样本生效日期，含当日；不得倒填为更早时点 */
    LocalDate effectiveFrom,

    /** 归属或样本失效日期，含当日；NULL表示未设结束日期 */
    LocalDate effectiveTo,

    /** 披露定义的完整成员数量，至少2只；用于核对样本完整性 */
    Integer expectedMembers,

    /** 内容SHA-256指纹，用于去重和定位不可变证据 */
    String contentHash,

    /** 样本完整定义JSON，含原始链接、页码、日期、PDF指纹、边界及全部成员 */
    String definitionJson,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 来源文档的原始链接。 */
    String sourceUrl,

    /** 来源文档标题。 */
    String sourceTitle,

    /** 原文公开时间。 */
    LocalDateTime sourcePublishedAt,

    /** 原文实际采集时间。 */
    LocalDateTime sourceFetchedAt) {
  /**
   * 显式转换包含公开文档信息的披露样本；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 包含公开文档信息的披露样本，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static CohortView from(CohortDetailDO row) {
    return new CohortView(
        row.getId(),
        row.getThemeId(),
        row.getCode(),
        row.getScope(),
        row.getTitle(),
        row.getSourceDocumentId(),
        row.getAvailableAt(),
        row.getEffectiveFrom(),
        row.getEffectiveTo(),
        row.getExpectedMembers(),
        row.getContentHash(),
        row.getDefinitionJson(),
        row.getCreatedAt(),
        row.getSourceUrl(),
        row.getSourceTitle(),
        row.getSourcePublishedAt(),
        row.getSourceFetchedAt());
  }
}
