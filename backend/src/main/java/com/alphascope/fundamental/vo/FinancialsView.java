package com.alphascope.fundamental.vo;

import java.util.List;

/**
 * 累计财报查询结果及计量单位。
 *
 * @author chenxuefeng
 */
public record FinancialsView(
    /** 截至业务时点可见的财报版本。 */
    List<FinancialView> items,

    /** 金额单位 CNY，人民币元。 */
    String amountUnit,

    /** 比例使用 DECIMAL 小数。 */
    String ratioUnit) {}
