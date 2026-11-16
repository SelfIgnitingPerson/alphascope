package com.alphascope.scheduler.dto;

/**
 * 流水线产物及质量，队列服务负责以进程归属和停止意图为前提原子提交。
 *
 * @author chenxuefeng
 */
public record PipelineOutcome(
    /** 最终执行结果。 */
    JobStatus status,
    /** 独立证据质量。 */
    DataQualityStatus quality,
    /** 公开原因，可空。 */
    String error,
    /** 成功时实际输入指纹。 */
    String inputHash) {}
