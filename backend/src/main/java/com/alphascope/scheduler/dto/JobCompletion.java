package com.alphascope.scheduler.dto;

import lombok.Builder;

/**
 * 任务最终提交命令，在一个SQL中保存执行结果和独立数据质量。
 *
 * @author chenxuefeng
 */
@Builder
public record JobCompletion(
    /** 持久任务主键。 */
    long jobId,
    /** 仍持有本任务的进程标识，防止过期进程覆盖结果。 */
    String ownerId,
    /** 最终执行状态。 */
    JobStatus status,
    /** 聚合后的数据质量。 */
    DataQualityStatus dataStatus,
    /** 可公开的失败或终止说明；正常执行时为空。 */
    String error) {}
