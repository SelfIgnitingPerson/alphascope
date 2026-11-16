package com.alphascope.intelligence.dto;

import com.alphascope.intelligence.vo.PriceVolumeStudy;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 条件式策略计算输入；参考区间在确认日之前形成，全部价格使用当前报告因子锚点。
 *
 * @author chenxuefeng
 */
@Builder(toBuilder = true)
public record PositionStrategyInput(
    /** 已通过近两月日历、行情及因子验证的同一份冻结量价研究。 */
    PriceVolumeStudy study,
    /** 前一有效交易日的同锚点复权收盘价，元。 */
    double previousClose,
    /** 截止日同锚点复权最低价，元。 */
    double currentLow,
    /** 截止日同锚点复权收益，小数比例。 */
    double dayReturn,
    /** 截止日前已形成的压力区间，供首次突破核验。 */
    PriceVolumeStudy.Zone breakoutReference,
    /** 两个确认日之前已形成的压力区间，供连续收复及回踩核验。 */
    PriceVolumeStudy.Zone addReference,
    /** 建仓突破参考最后使用的交易日期，必须早于截止日。 */
    LocalDate breakoutReferenceAsOf,
    /** 加仓参考最后使用的交易日期，必须早于前一有效交易日。 */
    LocalDate addReferenceAsOf,
    /** 主量价计算确认的收盘止损触发；没有有效止损时为空。 */
    Boolean stopTriggered,
    /** 主量价计算唯一判定的放量走弱组合，避免显示舍入值造成复算分歧。 */
    Boolean weakSelling,
    /** 主量价计算唯一判定的缩量反弹遇压组合。 */
    Boolean shrinkingRally) {}
