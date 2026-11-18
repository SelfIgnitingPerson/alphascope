package com.alphascope.intelligence.vo;

import java.util.List;
import lombok.Builder;

/**
 * 依据已核验事实形成的观察顺序，不是风险概率、收益评分或个人交易指令。
 *
 * @author chenxuefeng
 */
@Builder(toBuilder = true)
public record ResearchPriority(
    /** STRUCTURE_RISK、BUSINESS_RISK、CONFIRMED_RISK、TREND_WATCH、ENTRY_MATCH、DATA_GAP或WAIT。 */
    String code,
    /** FIRST优先核对、IMPORTANT重点关注、WATCH继续观察、DATA资料问题。 */
    String level,
    /** 用事实描述当前最需要核对的事项。 */
    String title,
    /** 生产方案原有结论；不由标题推断更多交易含义。 */
    String explanation,
    /** 该事项所属周期和方案代码。 */
    String horizon,
    String actionCode,
    /** 最多三项直接事实，保留实际数值和缺项语义。 */
    List<String> facts,
    /** 最多两项后续核查动作，不自动采集或提交订单。 */
    List<String> nextChecks) {}
