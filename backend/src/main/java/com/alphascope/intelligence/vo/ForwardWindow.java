package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.Set;

/**
 * 单个冻结信号的窗口结果，不等同实盘成交或个人仓位建议。
 *
 * @author chenxuefeng
 * @param status CALCULATED、WAITING_WINDOW、INSUFFICIENT_DATA或UNFILLED
 * @param reason 成交或排除原因
 * @param signalDate 实际信号日
 * @param entryDate 次个市场开市日
 * @param exitDate 持有至窗口末日收盘
 * @param windowDays 市场交易日窗口
 * @param netReturn 名义账户扣费收益
 * @param benchmarkReturn 同期指数开盘到收盘价格收益
 * @param maxDrawdown 日收盘净权益最大回撤
 * @param fees 佣金与印花税，滑点另含在成交假设中
 * @param inputSources 本次实际价格来源
 */
public record ForwardWindow(
    String status,
    String reason,
    LocalDate signalDate,
    LocalDate entryDate,
    LocalDate exitDate,
    int windowDays,
    Double netReturn,
    Double benchmarkReturn,
    Double maxDrawdown,
    Double fees,
    Set<String> inputSources) {}
