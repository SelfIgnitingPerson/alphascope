package com.alphascope.industry.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 登记有公开来源和明确计量单位的产业指标。
 *
 * @author chenxuefeng
 */
public record MetricInput(
    /** 主题稳定代码，例如PCB或COOLING。 */
    @NotBlank String themeCode,

    /** 产业指标稳定代码，需与unit共同解释。 */
    @NotBlank String metricCode,

    /** 本次资产或产业指标登记对应的业务日期。 */
    @NotNull LocalDate asOfDate,

    /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
    @NotNull LocalDateTime publishedAt,

    /** 指标数值；单位由unit给出，缺失时不得补成0。 */
    @NotNull BigDecimal value,

    /** 指标计量单位，例如元、股、倍或小数比例。 */
    @NotBlank String unit,

    /** 已登记原始来源文档的主键；产业指标必须能够回溯来源。 */
    @Min(1) long sourceId) {}
