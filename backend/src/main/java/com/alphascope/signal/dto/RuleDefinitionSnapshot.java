package com.alphascope.signal.dto;

import java.util.Map;

/**
 * 计算当次采用的规则、技术证据算法和参数；离线复算不读取现行配置。
 *
 * @author chenxuefeng
 */
public record RuleDefinitionSnapshot(
    /** 稳定规则版本，改变技术判定口径时升级。 */
    String ruleVersion,
    /** 技术证据算法版本。 */
    String technicalVersion,
    /** 当次完整阈值。 */
    Map<String, Double> parameters) {}
