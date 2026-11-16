package com.alphascope.backtest.dto;

import com.alphascope.signal.dto.RuleDefinitionSnapshot;

/**
 * 排队时冻结的研究请求和规则，执行与重启不能悄悄换用新参数。
 *
 * @author chenxuefeng
 */
public record ValidationCommand(
    /** 起止日期、范围及已经解析的证券名单。 */
    ResearchValidationRequest request,
    /** 接收任务时冻结的规则及技术算法。 */
    RuleDefinitionSnapshot definition) {}
