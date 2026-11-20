package com.alphascope.planning.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 仅批量读取当前页计划的最近复核，所有关联表继续应用租户隔离。
 *
 * @author chenxuefeng
 * @param asOf 截止业务日，为空时读取最近业务日
 * @param planIds 当前用户分页结果中的计划主键，调用前保证非空
 * @param status 仅查找指定结论，为空时读取最新复核
 */
public record PlanReviewBatchQuery(List<Long> planIds, LocalDate asOf, String status) {
  /** 读取各计划最近保存的复核。 */
  public PlanReviewBatchQuery(List<Long> planIds, LocalDate asOf) {
    this(planIds, asOf, null);
  }
}
