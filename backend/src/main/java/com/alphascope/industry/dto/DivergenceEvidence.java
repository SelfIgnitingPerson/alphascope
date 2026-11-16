package com.alphascope.industry.dto;

import java.util.List;
import java.util.Map;

/**
 * 同一主题最近三个交易日的规则输入及缺失诊断，禁止跨主题拼接。
 *
 * @author chenxuefeng
 */
public record DivergenceEvidence(
    /** 当前具备完整多成员及连续日期条件的主题规则数值。 */
    Map<String, Double> values,

    /** 有效主题研究样本或其证据诊断列表。 */
    List<ThemeDiagnostic> themes,

    /** 当前判定成立或无法判定的具体原因。 */
    String reason) {}
