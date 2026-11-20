package com.alphascope.portfolio.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 人工登记投资资产总额的请求；仅在显式启用持仓研究时用于集中度计算。
 *
 * @author chenxuefeng
 */
public record AccountInput(
    /** 手工登记的投资资产总额，单位元；该写入请求要求正数，不能以0代替未知。 */
    BigDecimal totalAssets,

    /** 本次资产或产业指标登记对应的业务日期。 */
    @NotNull LocalDate asOfDate,
    /** 用户选择的组合风险预算，元；不表示系统建议。 */
    @jakarta.validation.constraints.DecimalMin("0") BigDecimal riskBudget,
    /** 近期用途预留，元；未知时留空。 */
    @jakarta.validation.constraints.DecimalMin("0") BigDecimal liquidityReserve,
    /** 预计用款日，可为空。 */
    LocalDate fundsNeededOn,
    /** 资金用途，仅当前身份可见。 */
    @jakarta.validation.constraints.Size(max = 300) String fundsPurpose,
    /** 实际现金余额，未知留空，不以剩余资产推算。 */
    @jakarta.validation.constraints.DecimalMin("0") BigDecimal cashBalance,
    /** 人工确认是否已经登记全部持仓。 */
    Boolean positionsComplete) {
  /** 保留旧请求和冻结登记构造，新增个人资料保持未知。 */
  public AccountInput(
      BigDecimal totalAssets,
      LocalDate asOfDate,
      BigDecimal riskBudget,
      BigDecimal liquidityReserve,
      LocalDate fundsNeededOn,
      String fundsPurpose) {
    this(
        totalAssets,
        asOfDate,
        riskBudget,
        liquidityReserve,
        fundsNeededOn,
        fundsPurpose,
        null,
        null);
  }
}
