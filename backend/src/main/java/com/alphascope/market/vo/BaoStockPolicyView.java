package com.alphascope.market.vo;

import com.alphascope.common.diagnostic.FailureDiagnosis;

/**
 * 免费节点本机请求预算及保护状态；不是同一出口IP的供应商总计数，也不证明当前上游已恢复。
 *
 * @author chenxuefeng
 */
public record BaoStockPolicyView(
    /** 上海业务自然日。 */
    String date,
    /** 本机保守请求预算。 */
    int limit,
    /** 当日已预占请求数，含失败请求；读取失败为null。 */
    Integer used,
    /** 当日本机剩余额度，读取失败为null。 */
    Integer remaining,
    /** 本机是否仍保持访问保护，不等同当前上游限制状态。 */
    Boolean accessProtected,
    /** 状态读取异常；不能把损坏记录视为零请求或未限制。 */
    String error,
    /** 已保存的上游拒绝代码；未记录时为空。 */
    String accessCode,
    /** 原始拒绝观察时间；查询不更新，不等同本次请求时间。 */
    java.time.Instant accessObservedAt,
    /** 原始拒绝的供应商码、含义与安全原文；缺原文明确说明。 */
    FailureDiagnosis.ProviderError accessError) {}
