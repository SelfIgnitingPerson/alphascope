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
  /** 单账户固定主键1，不自增 */
  private Long id;

  /** 人工登记的投资资产总额，人民币元；NULL表示未知，已填须大于0 */
  private BigDecimal totalAssets;

  /** 人工登记或指标对应的业务日期，非数据库更新时间 */
  private LocalDate asOfDate;
}
