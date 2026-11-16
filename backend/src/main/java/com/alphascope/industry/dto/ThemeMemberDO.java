package com.alphascope.industry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 固定结构的数据传输对象，禁止使用无类型Map代替。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ThemeMemberDO {
  /** 成员证券代码。 */
  private String symbol;

  /** 成员中文名称。 */
  private String name;

  /** 披露研究样本ID；人工归属时为0。 */
  private Long cohortId;

  /** 成员名单的覆盖范围。 */
  private String scope;

  /** 支持披露样本的来源ID。 */
  private Long sourceDocumentId;

  /** 原文链接。 */
  private String sourceUrl;
}
