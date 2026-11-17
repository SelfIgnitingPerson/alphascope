package com.alphascope.backtest.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * 验证任务公开状态，隐藏归属令牌、去重键及完整原始证据。
 *
 * @author chenxuefeng
 */
@Builder
public record ValidationTaskView(
    /** 任务主键。 */
    long id,
    /** 持久执行状态。 */
    String status,
    /** 验证类型。 */
    String scope,
    /** 排队时冻结的研究开始日。 */
    LocalDate start,
    /** 排队时冻结的评估截止日。 */
    LocalDate end,
    /** 排队时冻结的证券名单。 */
    List<String> symbols,
    /** 冻结的真实数据范围。 */
    String dataScope,
    /** 当前阶段实际完成量。 */
    int completed,
    /** 当前阶段总量，0代表尚未确定。 */
    int total,
    /** 具体执行动作或失败原因。 */
    String message,
    /** 接收时间。 */
    LocalDateTime createdAt,
    /** 开始时间。 */
    LocalDateTime startedAt,
    /** 完成时间。 */
    LocalDateTime finishedAt,
    /** 最近心跳。 */
    LocalDateTime heartbeatAt,
    /** 不随心跳延长的截止时间。 */
    LocalDateTime deadlineAt,
    /** 已发布成功证据的校验值。 */
    String evidenceSha256,
    /** 只在明确详情查询加载的轻量结果。 */
    ValidationSummary summary,
    /** 详情实际依赖来源；从该任务冻结证据提取，不采用当前配置。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Set<String> inputSources) {}
