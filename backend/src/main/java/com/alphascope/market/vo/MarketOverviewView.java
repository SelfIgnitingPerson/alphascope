package com.alphascope.market.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 市场评级与独立市场事实分别展示。
 *
 * @author chenxuefeng
 */
public record MarketOverviewView(
    /** 查询业务日期，上海时区。 */
    LocalDate businessDate,

    /** 全 A 五维评级；维度缺失时保持 UNKNOWN。 */
    String state,

    /** 该日已保存的评分及证据。 */
    List<MarketScoreView> items,

    /** 当前供应商组合。 */
    String provider,

    /** 是否为演示模式。 */
    boolean isSynthetic,

    /** 金额单位 CNY。 */
    String amountUnit,

    /** 数据完整性状态。 */
    String dataStatus,

    /** 独立沪深市场截面，不等同完整全 A。 */
    MarketBreadthView snapshot,

    /** 已保存的分项市场观察。 */
    MarketObservationView observation) {}
