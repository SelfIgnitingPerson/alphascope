package com.alphascope.intelligence.vo;

import java.time.LocalDateTime;

/**
 * 评估结果携带实际判断的身份、方法和形成时间，不能混合不同版本。
 *
 * @author chenxuefeng
 * @param observationId 冻结判断编号
 * @param symbol 标的
 * @param methodVersion 判断规则版本
 * @param source 实际标的价格源
 * @param horizon 周期
 * @param actionCode 原始行动
 * @param observedAt 判断实际形成时间
 * @param protocolVersion 同时冻结的执行协议
 * @param window 窗口结果
 */
public record ForwardOutcome(
    long observationId,
    String symbol,
    String methodVersion,
    String source,
    String horizon,
    String actionCode,
    LocalDateTime observedAt,
    String protocolVersion,
    ForwardWindow window) {}
