package com.alphascope.market.dto;

import java.time.LocalDate;

/**
 * 已确认覆盖范围的同源复权输入，标准化数据与原始响应共同保存。
 *
 * @author chenxuefeng
 */
public record ResearchFactorWindow(
    /** 实际供应商代码。 */
    String source,
    /** 最早可用于计算的日期；范围前不能默认因子为1。 */
    LocalDate coverageFrom,
    /** 本次实际核验截止日期。 */
    LocalDate verifiedThrough,
    /** 标准化复权事件数组JSON。 */
    String eventsJson,
    /** 供应商原始响应JSON。 */
    String upstreamJson) {}
