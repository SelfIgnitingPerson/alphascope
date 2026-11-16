package com.alphascope.intelligence.vo;

import java.util.List;

/**
 * 自选研究优先级和验证条件，不读取个人资产、不生成委托指令。
 *
 * @author chenxuefeng
 */
public record ResearchAdvice(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name,

    /** 人工研究优先级，不是个人仓位建议或交易指令。 */
    String priority,

    /** 基于冻结证据确定的本期研究重点，不构成下单指令。 */
    String conclusion,

    /** 用于支持研究结论的已观测事实列表。 */
    List<String> observations,

    /** 待验证的解释假设；不能当作已经证实的事实。 */
    List<String> hypotheses,

    /** 下一步可具体执行的人工核查事项。 */
    List<String> verifyNext,

    /** 哪些新证据或指标变化出现后需要调整当前判断。 */
    List<String> reconsiderIf) {}
