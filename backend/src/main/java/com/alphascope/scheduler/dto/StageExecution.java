package com.alphascope.scheduler.dto;

import com.alphascope.common.task.PipelineStage;
import java.util.function.Supplier;
import lombok.Builder;

/**
 * 调度内部的阶段执行描述，不作为HTTP请求或JSON响应。
 *
 * @author chenxuefeng
 */
@Builder
public record StageExecution(
    /** 任务主键。 */
    long jobId,
    /** 运行进程归属。 */
    String ownerId,
    /** 受支持的阶段代码。 */
    PipelineStage stage,
    /** 可空原尝试主键。 */
    Long retryOf,
    /** 当前阶段的业务动作。 */
    Runnable action,
    /** 动作结束后的独立数据核验。 */
    Supplier<StageQuality> quality) {}
