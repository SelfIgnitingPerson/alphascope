package com.alphascope.backtest.vo;

import java.time.LocalDate;
import java.util.Map;

/**
 * 预先按时间分割的研究验证区间；没有训练过程，不将早期区间伪称模型训练集。
 *
 * @author chenxuefeng
 */
public record ValidationWindow(
    /** EARLY较早区间或HOLDOUT较晚留出区间。 */
    String label,
    /** 区间开始业务日期，包含当日。 */
    LocalDate start,
    /** 区间结束业务日期，包含当日。 */
    LocalDate end,
    /** 按后续交易窗口和规则代码索引的统计，不合并不同规则版本。 */
    Map<String, Map<String, SignalStatisticView>> statistics) {}
