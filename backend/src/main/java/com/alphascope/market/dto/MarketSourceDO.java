package com.alphascope.market.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class MarketSourceDO {
  /** 公开标的代码。 */
  private String symbol;

  /** 最新记录的实际供应商。 */
  private String source;

  /** 最新记录的交易日。 */
  private LocalDate tradeDate;

  /** 原始采集时刻。 */
  private LocalDateTime fetchedAt;

  /** 原始写入批次。 */
  private String batchId;
}
