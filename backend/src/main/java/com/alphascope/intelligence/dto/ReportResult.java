package com.alphascope.intelligence.dto;

import java.util.List;

/**
 * 所有引用只允许来自输入；事实、推断、风险与待验证事项分开保存。
 *
 * @author chenxuefeng
 */
public record ReportResult(
    /** 完整全市场评级；证据不足必须保留UNKNOWN。 */
    String marketState,

    /** 只解释已提供事实的报告文字摘要。 */
    String summary,

    /** 有冻结证据支持的积极因素；没有依据时为空列表。 */
    List<String> positiveFactors,

    /** 有冻结证据支持的不利因素。 */
    List<String> negativeFactors,

    /** 主题观测与待验证推断，不冒充全行业结论。 */
    List<String> themeOutlook,

    /** 当前关注标的的规则风险及证据边界。 */
    List<String> watchlistRisks,

    /** 下一步应补齐或复核的具体事项。 */
    List<String> nextChecks,

    /** 本次摘要允许引用的来源标识白名单。 */
    List<String> sourceIds,

    /** 尚未验证的假设、数据缺口和方法限制。 */
    List<String> uncertainties) {}
