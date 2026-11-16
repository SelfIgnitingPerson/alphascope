package com.alphascope.indicator.dto;

import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 生产与回顾共用的技术证据窗口；日历只允许使用已保存的同源开市及停牌证据。
 *
 * @author chenxuefeng
 */
public record TechnicalWindow(
    /** 证据所属业务日，不使用该日之后的价格。 */
    LocalDate date,
    /** 单一来源的标的原价和因子。 */
    List<MarketBarDO> bars,
    /** 单一来源的沪深300原价和因子。 */
    List<MarketBarDO> benchmark,
    /** 本窗口应有交易日；确认停牌已由调用方按实际来源排除。 */
    Set<LocalDate> expectedDates) {}
