package com.alphascope.industry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 人工登记标的与主题的真实有效归属区间。
 *
 * @author chenxuefeng
 */
public record MembershipInput(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    @NotBlank String symbol,

    /** 人工登记归属的主题代码；只改变真实生效区间，不倒填历史。 */
    @NotBlank String themeCode,

    /** 业务归属真实生效日期，包含当日，不能倒填。 */
    @NotNull LocalDate effectiveFrom,

    /** 业务归属结束日期，包含当日；为空表示未设截止。 */
    LocalDate effectiveTo) {}
