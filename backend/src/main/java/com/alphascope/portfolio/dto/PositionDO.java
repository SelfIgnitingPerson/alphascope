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
  /** 人工相关风险标签，可重叠，不是官方行业。 */
  private String riskGroup;

  /** 历史账本证明确实已于研究时点登记；仅内部计算使用。 */
  @com.fasterxml.jackson.annotation.JsonIgnore private boolean historyVerified;

  /** 本版本实际记录时刻，旧行缺省，不证明历史可得性。 */
  @com.fasterxml.jackson.annotation.JsonIgnore private java.time.LocalDateTime recordedAt;

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
