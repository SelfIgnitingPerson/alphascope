package com.alphascope.market.dto;

import com.alphascope.market.vo.MarketObservation;
import java.time.LocalDate;

/**
 * 五维模型的明确输入契约，覆盖证明与指标值分开校验。
 *
 * @author chenxuefeng
 */
public record ScopedScoreInput(
    /** 计算业务日期。 */
    LocalDate businessDate,
    /** 目标完整市场范围及分母。 */
    ScoreCoverage universe,
    /** 是否为演示输入。 */
    boolean synthetic,
    /** 同日指数、量能及均线广度。 */
    MarketObservation observation,
    /** 同日主题相对强度和范围。 */
    ThemeScoreEvidence themes,
    /** 同日领先样本及范围。 */
    LeaderScoreEvidence leaders) {}
