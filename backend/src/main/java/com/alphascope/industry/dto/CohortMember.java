package com.alphascope.industry.dto;

/**
 * 公开披露研究样本中一只已核验证券的代码与名称。
 *
 * @author chenxuefeng
 */
public record CohortMember(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name) {}
