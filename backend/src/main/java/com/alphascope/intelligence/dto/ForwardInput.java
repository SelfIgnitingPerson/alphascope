package com.alphascope.intelligence.dto;

import com.alphascope.market.dto.CalendarRevisionDO;
import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.List;

/**
 * 不可变窗口的完整可复算输入，原判断与未来实际价格分别保存。
 *
 * @author chenxuefeng
 * @param observationHash 实际形成判断的原始指纹
 * @param symbol 标的
 * @param signalDate 实际形成信号的业务日
 * @param source 冻结原价来源
 * @param frozenClose 信号形成时的未复权收盘价
 * @param frozenFactor 信号形成时的同源因子
 * @param windowDays 权威市场交易日数量
 * @param protocol 同时冻结的执行协议
 * @param dates 信号后至窗口终点的市场开市日
 * @param bars 同源标的原价、因子与成交额
 * @param benchmark 相同窗口基准价格，缺失不填零
 * @param calendar 含休市日与实际来源的连续日历证据
 */
public record ForwardInput(
    String observationHash,
    String symbol,
    LocalDate signalDate,
    String source,
    Double frozenClose,
    Double frozenFactor,
    int windowDays,
    ForwardProtocol protocol,
    List<LocalDate> dates,
    List<MarketBarDO> bars,
    List<MarketBarDO> benchmark,
    List<CalendarRevisionDO> calendar) {}
