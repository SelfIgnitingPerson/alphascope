package com.alphascope.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class PositionDO {
  /** 持仓标的代码。 */
  private String symbol;

  /** 标的中文名称。 */
  private String name;

  /** 人工持仓数量，单位股。 */
  private BigDecimal quantity;

  /** 人工每股成本，单位元。 */
  private BigDecimal costPrice;

  /** 登记的业务日期。 */
  private LocalDate asOfDate;
}
