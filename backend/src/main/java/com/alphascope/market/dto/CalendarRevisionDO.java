package com.alphascope.market.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已知时点的权威交易日历行
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
public class CalendarRevisionDO {
  /** 日历记录主键。 */
  private Long id;

  /** 权威日历日期。 */
  private LocalDate tradeDate;

  /** 已保存的开闭市状态。 */
  private Boolean open;

  /** 实际来源。 */
  private String source;

  /** 该版本保存时刻。 */
  private LocalDateTime updatedAt;
}
