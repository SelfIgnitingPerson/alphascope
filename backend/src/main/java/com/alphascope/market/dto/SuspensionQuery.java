package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 指定来源与模式下的停牌日期区间；缺少证据不能当作停牌。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class SuspensionQuery {
  /** 证券主键，关联 instrument.id。 */
  private long instrumentId;

  /** 与查询行情一致的实际证据来源。 */
  private String source;

  /** 真实与演示证据的隔离标志。 */
  private boolean synthetic;

  /** 要求核对停牌证据的窗口起始日。 */
  private LocalDate startDate;

  /** 要求核对停牌证据的窗口结束日。 */
  private LocalDate endDate;
}
