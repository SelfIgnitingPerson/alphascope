package com.alphascope.planning.dto;

import java.util.List;

/**
 * 仅批量读取当前页计划的最近复核，所有关联表继续应用租户隔离。
 *
 * @author chenxuefeng
 * @param planIds 当前用户分页结果中的计划主键，调用前保证非空
 */
public record PlanReviewBatchQuery(List<Long> planIds) {}
