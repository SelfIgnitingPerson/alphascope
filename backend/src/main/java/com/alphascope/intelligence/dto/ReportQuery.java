package com.alphascope.intelligence.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 报告归档分页与总数统计共用筛选条件，防止两次查询口径漂移。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class ReportQuery {
  /** 报告类型 DAILY 或 WEEKLY。 */
  private String reportType;

  /** 报告业务日期下限，包含当日。 */
  private LocalDate startDate;

  /** 报告业务日期上限，包含当日。 */
  private LocalDate endDate;

  /** 分页最多返回条数，由 Service 校验为 1 至 100。 */
  private int size;

  /** 从 0 开始的分页偏移，统计总数时不使用。 */
  private int offset;

  /** 仅查询指定真实或演示模式。 */
  private boolean synthetic;

  /** true 仅返回同类型、同业务日、同模式的最新修订。 */
  private boolean latestOnly;
}
