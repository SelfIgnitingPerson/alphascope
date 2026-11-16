package com.alphascope.market.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 同一次行情落库的当前值、内容指纹和可选修订审计；由短事务整体保存。
 *
 * @author chenxuefeng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MarketBarWriteCommand extends MarketBarDO {
  /** 修订前的整条行情 JSON；首次采集时为空。 */
  private String oldValue;

  /** 本次采集的整条行情 JSON，不含后续生成的审计字段。 */
  private String newValue;

  /** OHLC、量额及复权因子共同计算的 SHA-256。 */
  private String contentHash;
}
