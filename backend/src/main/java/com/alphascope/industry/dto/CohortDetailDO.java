package com.alphascope.industry.dto;

import com.alphascope.industry.entity.ThemeResearchCohort;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 联表查询结果，不增加额外持久化列。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@EqualsAndHashCode(callSuper = true)
public class CohortDetailDO extends ThemeResearchCohort {
  /** 来源文档的原始链接。 */
  private String sourceUrl;

  /** 来源文档标题。 */
  private String sourceTitle;

  /** 原文公开时间。 */
  private LocalDateTime sourcePublishedAt;

  /** 原文实际采集时间。 */
  private LocalDateTime sourceFetchedAt;
}
