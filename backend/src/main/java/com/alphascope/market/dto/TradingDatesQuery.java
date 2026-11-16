package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;

/**
 * 交易窗口要求：日期、上市范围和与原价一致的明确停牌来源。
 *
 * @author chenxuefeng
 */
@Builder
public record TradingDatesQuery(
    /** 统一证券代码。 */
    String symbol,
    /** 起始业务日期，包含当日。 */
    LocalDate start,
    /** 截止业务日期，包含当日。 */
    LocalDate end,
    /** 同源停牌证据来源；为空时只核对市场日历。 */
    String source) {}
