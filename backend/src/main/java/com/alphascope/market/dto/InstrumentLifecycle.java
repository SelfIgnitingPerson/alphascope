package com.alphascope.market.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 经实际来源验证的证券生命周期，不从首条K线推断。
 *
 * @author chenxuefeng
 * @param symbol 证券代码
 * @param name 供应商名称
 * @param listedAt 上市日期
 * @param delistedAt 明确退市日期，可空
 * @param source 实际元数据来源
 * @param observedAt 实际观测时刻
 * @param evidenceJson 原始响应和来源URL
 */
public record InstrumentLifecycle(
    String symbol,
    String name,
    LocalDate listedAt,
    LocalDate delistedAt,
    String source,
    LocalDateTime observedAt,
    String evidenceJson) {}
