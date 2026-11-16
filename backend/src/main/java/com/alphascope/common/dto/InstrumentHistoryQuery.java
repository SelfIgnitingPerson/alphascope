package com.alphascope.common.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 单只证券的日期区间及模式查询条件，供指标与规则历史共用。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class InstrumentHistoryQuery {
  /** 统一证券代码，例如 300476.SZ。 */
  private String symbol;

  /** 查询起始交易日期，包含当日。 */
  private LocalDate startDate;

  /** 查询结束交易日期，包含当日。 */
  private LocalDate endDate;

  /** 仅查询指定真实或演示模式的历史。 */
  private boolean synthetic;
}
