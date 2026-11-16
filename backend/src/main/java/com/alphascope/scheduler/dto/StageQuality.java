package com.alphascope.scheduler.dto;

import java.util.List;

/**
 * 单阶段证据核验结果；成功执行不强行提升数据质量。
 *
 * @author chenxuefeng
 */
public record StageQuality(
    /** 阶段实际数据质量。 */
    DataQualityStatus status,
    /** 满足阶段核验要求的数据单元数量。 */
    int available,
    /** 本阶段应核验的数据单元数量。 */
    int expected,
    /** 缺失、范围不足或无法验证的具体诊断。 */
    List<StageQualityIssue> issues,
    /** 阶段数据口径或缓存使用说明。 */
    String note) {
  /**
   * 构建没有额外数据源覆盖要求的执行确认结果。
   *
   * @param note 执行和核验含义
   * @return 单元已核验的数据质量结果
   */
  public static StageQuality available(String note) {
    return new StageQuality(DataQualityStatus.AVAILABLE, 1, 1, List.of(), note);
  }
}
