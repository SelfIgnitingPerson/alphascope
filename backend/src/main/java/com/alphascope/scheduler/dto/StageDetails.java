package com.alphascope.scheduler.dto;

import com.alphascope.common.task.ExecutionProgress;
import java.time.Instant;

/**
 * 阶段持久详情，区分心跳、实际进展、截止时间和最终数据质量。
 *
 * @author chenxuefeng
 */
public record StageDetails(
    /** 最终核验结果；运行中可为空。 */
    StageQuality quality,
    /** 最近实际工作进展；不能用心跳冒充。 */
    ExecutionProgress progress,
    /** 本阶段硬截止时刻，心跳不会延长。 */
    Instant deadline,
    /** 原尝试主键；首次任务为空。 */
    Long retryOf,
    /** FRESH_RUN或REVALIDATE_STORED_INPUTS；恢复不盲目跳过输入核验。 */
    String recoveryMode,
    /** 失败时冻结的公开诊断，旧记录及成功阶段可为空。 */
    com.alphascope.common.diagnostic.FailureDiagnosis diagnostic) {
  /** 兼容不含诊断的旧阶段构造入口，缺项不补推为已知原因。 */
  public StageDetails(
      StageQuality quality,
      ExecutionProgress progress,
      Instant deadline,
      Long retryOf,
      String recoveryMode) {
    this(quality, progress, deadline, retryOf, recoveryMode, null);
  }
}
