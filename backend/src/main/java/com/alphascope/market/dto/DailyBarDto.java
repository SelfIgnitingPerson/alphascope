package com.alphascope.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * OHLC人民币元，volume股，amount人民币元。factor与原价分开存储。
 *
 * @author chenxuefeng
 */
public record DailyBarDto(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 行情或指标所属交易日期，使用上海时区。 */
    LocalDate tradeDate,

    /** 未复权开盘价，单位人民币元。 */
    BigDecimal open,

    /** 未复权最高价，单位人民币元。 */
    BigDecimal high,

    /** 未复权最低价，单位人民币元。 */
    BigDecimal low,

    /** 未复权收盘价，单位人民币元；缺失时不能当作0。 */
    BigDecimal close,

    /** 成交量，单位股；供应商使用手时必须先换算。 */
    BigDecimal volume,

    /** 成交金额，单位人民币元；缺失时不补造0。 */
    BigDecimal amount,

    /** 与本条原价同源的正数复权因子；价格与因子分开保存。 */
    BigDecimal adjustmentFactor,

    /** 实际提供该窗口数据的供应商代码，不是多通道配置名称。 */
    String provider,

    /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
    LocalDateTime fetchedAt,

    /** 是否为演示数据；真实数据和演示数据必须隔离。 */
    boolean isSynthetic) {}
