package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 交易日历写入内容，日期、模式和来源必须一起保存。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class TradingCalendarWriteCommand {
  /** 交易日历对应日期，上海时区。 */
  private LocalDate tradeDate;

  /** 供应商明确返回的开市标志，不能按星期推断。 */
  private boolean open;

  /** 是否为演示模式，与真实日历隔离。 */
  private boolean synthetic;

  /** 本次实际提供日历的供应商。 */
  private String source;
}
