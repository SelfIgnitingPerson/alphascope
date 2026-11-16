package com.alphascope.market.dto;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 严格时点行情修订联查结果
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
public class MarketRevisionDO {
  /** 不可变行情修订主键。 */
  private Long id;

  /** 包含原价和同源因子的冻结原文。 */
  private String barJson;

  /** 真实入库时刻。 */
  private LocalDateTime recordedAt;
}
