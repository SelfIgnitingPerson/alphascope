package com.alphascope.intelligence.dto;

import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * 近两个月量价研究的冻结输入；交易日已经扣除同源证实停牌，不读取持仓或后续行情。
 *
 * @author chenxuefeng
 */
@Builder
public record PriceVolumeWindow(
    /** 统一证券代码。 */
    String symbol,
    /** 证券名称。 */
    String name,
    /** 研究截止交易日，上海业务日期。 */
    LocalDate date,
    /** 同源原价、人民币成交额、原股数和因子；允许含窗口外记录，计算时按截止日期过滤。 */
    List<MarketBarDO> bars,
    /** 两个自然月内应有交易日，空值表示没有可核验日历。 */
    Set<LocalDate> expectedDates,
    /** 截止日是否有明确停牌证据，停牌时不生成当前交易判断。 */
    boolean suspended,
    /** 是否为演示输入；演示不生成真实交易参考。 */
    boolean synthetic) {}
