package com.alphascope.planning.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计划列表摘要，不加载或返回完整冻结正文。
 *
 * @author chenxuefeng
 * @param id 计划主键
 * @param symbol 证券代码
 * @param name 冻结时证券名称
 * @param horizon 研究周期
 * @param actionCode 行动代码
 * @param archived 是否归档
 * @param createdAt 实际保存时刻
 * @param asOf 原研究日期
 */
public record PlanSummary(
    long id,
    String symbol,
    String name,
    String horizon,
    String actionCode,
    boolean archived,
    LocalDateTime createdAt,
    LocalDate asOf) {}
