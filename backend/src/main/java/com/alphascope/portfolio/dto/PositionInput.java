package com.alphascope.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 人工登记当前持仓数量、成本和业务日期；不是券商流水或下单请求。
 *
 * @author chenxuefeng
 */
public record PositionInput(
    /** 当前手工持仓数量，单位股；必须非负。 */
    @NotNull @DecimalMin("0") BigDecimal quantity,

    /** 人工登记的每股成本，单位人民币元；必须非负。 */
    @NotNull @DecimalMin("0") BigDecimal costPrice,

    /** 本次资产或产业指标登记对应的业务日期。 */
    @NotNull LocalDate asOfDate,
    /** 人工相关风险标签，可用逗号分隔多项，不冒充官方行业分类。 */
    @jakarta.validation.constraints.Size(max = 256) String riskGroup) {
  /** 旧请求没有风险标签时保持未知。 */
  public PositionInput(BigDecimal quantity, BigDecimal costPrice, LocalDate asOfDate) {
    this(quantity, costPrice, asOfDate, null);
  }
}
