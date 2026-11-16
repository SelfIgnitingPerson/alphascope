package com.alphascope.backtest.vo;

/**
 * 完整链路复算结果，只证明冻结证据可复算，不证明投资方法有效。
 *
 * @author chenxuefeng
 */
public record ValidationVerification(
    /** 原始输入、逐日指标、规则、统计均匹配。 */
    boolean matched,
    /** 复算市场窗口数量。 */
    int marketWindows,
    /** 完成价格到规则全链路核对的股票数量。 */
    int technicalStocks,
    /** 实际技术证据算法。 */
    String technicalVersion) {}
