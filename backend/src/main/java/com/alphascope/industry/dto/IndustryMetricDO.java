package com.alphascope.industry.dto;

import com.alphascope.industry.entity.IndustryMetric;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
public class IndustryMetricDO extends IndustryMetric {
  /** 指标引用的原始文档标题。 */
  private String title;

  /** 指标引用的原始文档链接。 */
  private String url;
}
