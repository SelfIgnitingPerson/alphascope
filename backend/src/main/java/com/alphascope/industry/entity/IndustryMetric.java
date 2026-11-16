package com.alphascope.industry.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
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
public class IndustryMetric {
  /** 记录主键，自增生成 */
  private Long id;

  /** 主题主键，关联theme.id */
  private Long themeId;

  /** 产业指标代码，需结合unit解释业务口径 */
  private String metricCode;

  /** 人工登记或指标对应的业务日期，非数据库更新时间 */
  private LocalDate asOfDate;

  /** 原始材料公开发布时间，上海时区 */
  private LocalDateTime publishedAt;

  /** 产业指标数值，单位由unit指定；NULL表示缺失 */
  private BigDecimal value;

  /** 指标计量单位，例如元、吨或小数比例；不能跨单位直接比较 */
  private String unit;

  /** 来源文档主键，关联source_document.id */
  private Long sourceDocumentId;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;
}
