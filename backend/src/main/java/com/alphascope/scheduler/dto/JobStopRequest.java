package com.alphascope.scheduler.dto;

import lombok.Builder;

/**
 * 持久取消或超时请求，跨进程和重启后仍保留终止意图。
 *
 * @author chenxuefeng
 */
@Builder
public record JobStopRequest(
    /** 目标任务主键。 */
    long jobId,
    /** CANCEL_REQUESTED或TIMEOUT_REQUESTED。 */
    JobStatus status,
    /** 超时请求需校验进程归属；人工取消为空。 */
    String ownerId,
    /** 可公开的停止原因。 */
    String reason) {}
