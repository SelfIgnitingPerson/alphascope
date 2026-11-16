package com.alphascope.industry.vo;

import com.alphascope.industry.dto.IndustryMetricDO;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公开来源联查响应，保留文档链接和披露时点。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IndustryMetricView(
    /** 记录主键，自增生成 */
    Long id,

    /** 主题主键，关联theme.id */
    Long themeId,

    /** 产业指标代码，需结合unit解释业务口径 */
    String metricCode,

    /** 人工登记或指标对应的业务日期，非数据库更新时间 */
    LocalDate asOfDate,

    /** 原始材料公开发布时间，上海时区 */
    LocalDateTime publishedAt,

    /** 产业指标数值，单位由unit指定；NULL表示缺失 */
    BigDecimal value,

    /** 指标计量单位，例如元、吨或小数比例；不能跨单位直接比较 */
    String unit,

    /** 来源文档主键，关联source_document.id */
    Long sourceDocumentId,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 记录最后更新时间，上海时区 */
    LocalDateTime updatedAt,

    /** 指标引用的原始文档标题。 */
    String title,

    /** 指标引用的原始文档链接。 */
    String url) {
  /**
   * 显式转换包含公开文档信息的产业指标；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 包含公开文档信息的产业指标，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static IndustryMetricView from(IndustryMetricDO row) {
    return new IndustryMetricView(
        row.getId(),
        row.getThemeId(),
        row.getMetricCode(),
        row.getAsOfDate(),
        row.getPublishedAt(),
        row.getValue(),
        row.getUnit(),
        row.getSourceDocumentId(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getTitle(),
        row.getUrl());
  }
}
