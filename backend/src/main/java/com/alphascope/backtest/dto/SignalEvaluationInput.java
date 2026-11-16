package com.alphascope.backtest.dto;

import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.signal.entity.SignalEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * 纯历史统计的完整输入；所有行情和信号均来自已保存数据，计算过程不联网。
 *
 * @author chenxuefeng
 */
@Builder
public record SignalEvaluationInput(
    /** 信号日期、评估截止日期和可用性模式。 */
    SignalStatisticsRequest request,
    /** 评估截止前的标的同源原价和因子。 */
    List<MarketBarDO> bars,
    /** 同一窗口的沪深300行情；缺失时不补造基准收益。 */
    List<MarketBarDO> benchmark,
    /** 查询区间内已保存规则记录，包括未触发和证据不足。 */
    List<SignalEvent> signals,
    /** 权威日历中的开市日期；为空时不得把相邻已存K线当作连续交易日。 */
    Set<LocalDate> tradingDates,
    /** 用于提示小样本的最低数量，不代表满足该值就证明方法有效。 */
    int minimumSamples) {}
