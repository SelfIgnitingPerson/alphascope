package com.alphascope.industry.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ThemeResearchCohort {
  /** 记录主键，自增生成 */
  private Long id;

  /** 主题主键，关联theme.id */
  private Long themeId;

  /** 不可变研究样本唯一代码；来源或成员改变须新建样本标识 */
  private String code;

  /** 样本覆盖范围标识，当前为DISCLOSED_RESEARCH_SAMPLE披露研究样本 */
  private String scope;

  /** 研究样本中文名称 */
  private String title;

  /** 来源文档主键，关联source_document.id */
  private Long sourceDocumentId;

  /** 该样本公开依据最早可用于研究的时刻，上海时区 */
  private LocalDateTime availableAt;

  /** 归属或样本生效日期，含当日；不得倒填为更早时点 */
  private LocalDate effectiveFrom;

  /** 归属或样本失效日期，含当日；NULL表示未设结束日期 */
  private LocalDate effectiveTo;

  /** 披露定义的完整成员数量，至少2只；用于核对样本完整性 */
  private Integer expectedMembers;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 样本完整定义JSON，含原始链接、页码、日期、PDF指纹、边界及全部成员 */
  private String definitionJson;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;
}
