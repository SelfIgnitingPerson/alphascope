package com.alphascope.backtest.vo;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * 按规则和交易窗口计算的描述统计；样本数与方法有效性分别表达。
 *
 * @author chenxuefeng
 */
@Builder
public record SignalStatisticView(
    /** 实际成熟且没有重叠的窗口样本数。 */
    int sampleCount,
    /** 标的收盘到收盘收益均值，小数，未扣成本。 */
    Double meanReturn,
    /** 标的收益为正的比例，不等于可交易策略胜率。 */
    Double positiveRatio,
    /** OBSERVED、SMALL_SAMPLE、NO_TRIGGERED_SIGNALS、WAITING_WINDOW或INSUFFICIENT_DATA。 */
    String dataStatus,
    /** 收益中位数，小数；没有样本时为空。 */
    Double medianReturn,
    /** 与有效标的窗口日期对齐的沪深300平均收益；无可用基准时为空。 */
    Double meanBenchmarkReturn,
    /** 同一有效基准子样本的标的收益减基准收益的均值，小数。 */
    Double meanExcessReturn,
    /** 同时具备有效基准行情的样本数，不能把缺失基准当作0收益。 */
    int benchmarkSampleCount,
    /** 区间内该规则已保存记录数，包含未触发、证据不足及不适用记录。 */
    int recordCount,
    /** 该规则明确触发的记录数，未触发不作为收益样本。 */
    int triggeredCount,
    /** 各排除原因的记录数；样本数加排除数等于recordCount。 */
    Map<String, Integer> exclusions,
    /** 实际涉及的规则版本；缺少版本信息明确列为UNRECORDED。 */
    List<String> ruleVersions) {}
