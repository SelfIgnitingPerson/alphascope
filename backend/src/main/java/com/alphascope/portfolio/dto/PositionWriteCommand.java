package com.alphascope.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 人工持仓写入内容，只用于研究分析，不向券商下单。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class PositionWriteCommand {
  /** 证券主键，关联 instrument.id。 */
  private long instrumentId;

  /** 人工登记持仓数量，单位股，不能为负。 */
  private BigDecimal quantity;

  /** 每股成本，单位人民币元。 */
  private BigDecimal costPrice;

  /** 人工持仓对应的业务日期，上海时区。 */
  private LocalDate asOfDate;
}
