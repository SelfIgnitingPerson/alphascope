package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 在同一事务内新增停牌证据、清理停牌行情或删除已失效的停牌标记。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class SuspensionWriteCommand {
  /** 证券主键，关联 instrument.id。 */
  private long instrumentId;

  /** 具有停牌或有效交易证据的业务日期。 */
  private LocalDate tradeDate;

  /** 提供该证据的实际供应商，与行情来源一致。 */
  private String source;

  /** 真实与演示证据的隔离标志。 */
  private boolean synthetic;

  /** 新增停牌证据的采集批次；清理旧证据时不使用。 */
  private String batchId;
}
