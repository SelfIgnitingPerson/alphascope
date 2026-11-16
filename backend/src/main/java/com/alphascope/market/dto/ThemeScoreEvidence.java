package com.alphascope.market.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 主题相对强度的范围和有效样本，不把两份披露样本解释为全主题宇宙。
 *
 * @author chenxuefeng
 */
public record ThemeScoreEvidence(
    /** 实际业务日期。 */
    LocalDate businessDate,
    /** 范围和清单核验。 */
    ScoreCoverage coverage,
    /** 有效主题RS20为正的比例，小数。 */
    Double positiveRatio,
    /** 实际可用主题代码。 */
    List<String> codes) {}
