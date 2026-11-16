package com.alphascope.market.vo;

import java.util.List;

/**
 * 日线列表及明确的价格、数量和金额单位。
 *
 * @author chenxuefeng
 */
public record BarsResponse(
    /** 当前页或当前响应包含的业务记录列表。 */
    List<BarView> items,

    /** 价格单位标识，当前为CNY（人民币元）。 */
    String priceUnit,

    /** 数量单位标识，当前为SHARE（股）。 */
    String volumeUnit,

    /** 成交金额单位标识，当前为CNY（人民币元）。 */
    String amountUnit) {}
