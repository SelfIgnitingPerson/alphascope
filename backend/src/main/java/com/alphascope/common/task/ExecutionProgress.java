package com.alphascope.common.task;

import java.time.Instant;

/**
 * 后台阶段最近可确认的进度，不用心跳时间替代业务进展。
 *
 * @author chenxuefeng
 */
public record ExecutionProgress(
    /** 当前已处理数量。 */
    int completed,
    /** 本阶段已知总数量；0表示尚未确定。 */
    int total,
    /** 可公开的当前动作说明。 */
    String message,
    /** 最近一次真实进度更新时间。 */
    Instant updatedAt) {}
