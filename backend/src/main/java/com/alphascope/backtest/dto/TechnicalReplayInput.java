package com.alphascope.backtest.dto;

import com.alphascope.signal.dto.RuleDefinitionSnapshot;
import java.time.LocalDate;
import java.util.Set;

/**
 * 从价格重建技术规则的完整冻结输入，不保存虚构的历史信号。
 *
 * @author chenxuefeng
 */
public record TechnicalReplayInput(
    /** 原价、基准、市场日历和统计条件；signals必须为空，重建时生成。 */
    SignalEvaluationInput source,
    /** 唯一规则、技术算法和精度口径。 */
    RuleDefinitionSnapshot definition,
    /** 同源停牌证据排除后的标的应有交易日，统计窗口仍按市场日历成熟。 */
    Set<LocalDate> stockTradingDates) {}
