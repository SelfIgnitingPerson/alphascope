package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 同源原价及复权因子的完整历史窗口查询。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class MarketBarQuery {
  /** 统一证券代码，例如 000300.SH。 */
  private String symbol;

  /** 历史窗口起始日期，包含当日。 */
  private LocalDate startDate;

  /** 历史窗口结束日期，包含当日。 */
  private LocalDate endDate;

  /** 指定真实或演示模式，不得混用。 */
  private boolean synthetic;

  /** 整窗统一的实际行情来源，同时约束复权因子来源。 */
  private String source;
}
