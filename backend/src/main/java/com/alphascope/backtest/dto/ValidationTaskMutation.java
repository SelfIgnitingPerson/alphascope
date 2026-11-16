package com.alphascope.backtest.dto;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 验证任务的具名状态变更参数，避免Mapper长参数列表和无类型Map。
 *
 * @author chenxuefeng
 */
@Builder
public record ValidationTaskMutation(
    /** 任务主键。 */
    long id,
    /** 当前执行进程归属。 */
    String owner,
    /** 候选最终状态，SQL保留已经持久的停止意图。 */
    String status,
    /** 实际业务时区时间。 */
    LocalDateTime now,
    /** 不随心跳延长的截止时间。 */
    LocalDateTime deadline,
    /** 需要恢复的最晚失联心跳时间。 */
    LocalDateTime staleBefore,
    /** 本阶段完成数量。 */
    int completed,
    /** 本阶段总量。 */
    int total,
    /** 动作或失败摘要。 */
    String message,
    /** 轻量结果。 */
    String summary,
    /** 大证据单独存储。 */
    String evidence,
    /** 大证据文本SHA256。 */
    String evidenceHash) {}
