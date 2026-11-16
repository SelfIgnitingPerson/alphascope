package com.alphascope.market.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行情获取方式与原始采集时间分开保存；缓存复用不会刷新行情的fetched_at。
 *
 * @author chenxuefeng
 */
public record MarketAcquisition(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 本次行情获取方式：FETCHED新采集、FRESH_CACHE有效缓存、STORED_HISTORY已存真实历史。 */
    String mode,

    /** 该项证据的实际供应商或原始来源标识。 */
    String source,

    /** 本次完整性验证区间的起始业务日期，包含该日。 */
    LocalDate validatedFrom,

    /** 本次完整性验证区间的截止业务日期，包含该日。 */
    LocalDate validatedThrough,

    /** 该标的验证区间内真实保存的行情条数。 */
    int rows,

    /** 本窗口最早的原始采集时间；无记录时为空。 */
    LocalDateTime oldestFetchedAt,

    /** 本窗口最新的原始采集时间；无记录时为空。 */
    LocalDateTime newestFetchedAt,

    /** 获取方式及未联网复核等边界说明。 */
    String note) {}
