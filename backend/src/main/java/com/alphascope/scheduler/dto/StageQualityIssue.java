package com.alphascope.scheduler.dto;

/**
 * 可定位到证券或数据集的数据质量诊断，不包含密钥或上游响应正文。
 *
 * @author chenxuefeng
 */
public record StageQualityIssue(
    /** 证券代码或数据集范围。 */
    String scope,
    /** 实际来源；尚未获取时为空。 */
    String source,
    /** 来源或数据集的原始业务状态。 */
    String status,
    /** 可以公开解释的缺失或复用原因。 */
    String reason) {}
