package com.alphascope.portfolio.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class PortfolioAccount {
  /** 实际历史登记已核验，仅内部使用。 */
  @com.fasterxml.jackson.annotation.JsonIgnore private boolean historyVerified;

  /** 单账户固定主键1，不自增 */
  private Long id;

  /** 人工登记的投资资产总额，人民币元；NULL表示未知，已填须大于0 */
  private BigDecimal totalAssets;

  /** 实际现金余额，未知不从股票市值反推。 */
  private BigDecimal cashBalance;

  /** 用户是否确认已登记全部持仓。 */
  private Boolean positionsComplete;

  /** 用户自行设定的组合风险预算，元；未知不按零。 */
  private BigDecimal riskBudget;

  /** 用户登记的近期用款预留，元。 */
  private BigDecimal liquidityReserve;

  /** 用户登记的预计用款日，可未知。 */
  private LocalDate fundsNeededOn;

  /** 资金用途，仅当前身份可见。 */
  private String fundsPurpose;

  /** 人工登记或指标对应的业务日期，非数据库更新时间 */
  private LocalDate asOfDate;
}
