package com.alphascope.intelligence.dto;

import java.time.LocalDate;
import java.util.Set;
import lombok.Builder;

/**
 * 多周期研究的只读证据，价格、财报与规则均按同一截止日期核对。
 *
 * @author chenxuefeng
 */
@Builder
public record HoldingResearchInput(
    /** 已读取的证券、同源行情、财报与规则；不含个人仓位。 */
    StockEvidence stock,
    /** 精确业务截止日。 */
    LocalDate asOf,
    /** 两年区间按上市日与同源明确停牌扣除的应有交易日。 */
    Set<LocalDate> expectedDates,
    /** 演示模式禁止生成真实买卖参考价。 */
    boolean synthetic) {}
