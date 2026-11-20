package com.alphascope.fundamental.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * 长期情景保存时冻结的财报失效条件。
 *
 * @author chenxuefeng
 * @param minimumProfitYoy 归母利润同比下限，小数。空表示不启用
 * @param checkNegativeCashflow 是否核对经营现金流为负
 * @param consecutiveReports 需要连续满足同一风险条件的累计财报期数
 */
public record ValuationExitPolicy(
    @DecimalMin("-1") @DecimalMax("1") BigDecimal minimumProfitYoy,
    boolean checkNegativeCashflow,
    @Min(1) @Max(4) int consecutiveReports) {}
