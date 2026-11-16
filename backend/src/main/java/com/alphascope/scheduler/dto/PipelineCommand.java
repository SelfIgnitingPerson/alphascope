package com.alphascope.scheduler.dto;

import java.time.LocalDate;

/**
 * 一次已领取任务的流水线执行上下文，不包含HTTP可绑定的进程归属字段。
 *
 * @author chenxuefeng
 */
public record PipelineCommand(
    /** 持久任务主键。 */
    long jobId,
    /** DAILY或WEEKLY。 */
    String reportType,
    /** 用户请求业务日期。 */
    LocalDate businessDate,
    /** 可空父尝试。 */
    Long retryOf,
    /** 当前实际持有任务的进程标识。 */
    String ownerId) {}
