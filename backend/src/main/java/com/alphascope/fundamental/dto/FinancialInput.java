package com.alphascope.fundamental.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工录入的累计财报；必须附可核验公开来源，同报告期只新增内容版本，不覆盖历史。
 *
 * <p>金额统一使用人民币元，毛利率和同比增速使用小数。可空财务值表示尚无可用证据，不能写成0。
 *
 * @author chenxuefeng
 */
public record FinancialInput(
    /** 必填标的代码，例如300476.SZ；须已登记在标的主数据中。 */
    @NotBlank String symbol,

    /** 累计财报的报告期末日期，按同报告期进行同比比较。 */
    @NotNull LocalDate periodEnd,

    /** 累计口径：Q1_CUMULATIVE一季、H1_CUMULATIVE半年、Q3_CUMULATIVE三季或ANNUAL全年。 */
    @NotBlank @Pattern(regexp = "Q1_CUMULATIVE|H1_CUMULATIVE|Q3_CUMULATIVE|ANNUAL")
        String reportType,

    /** 必须填写原始公开披露时刻，上海时区；不能用录入时间替代公告时间。 */
    @NotNull LocalDateTime publishedAt,

    /** 同标的、报告期及报告类型的正整数内容版本；仅新增，不覆盖既有版本。 */
    @Min(1) int version,

    /** 累计营业收入，单位人民币元；为空表示尚无可用证据。 */
    BigDecimal revenue,

    /** 累计归属于母公司股东的净利润，单位人民币元；允许亏损，空值表示缺失。 */
    BigDecimal netProfit,

    /** 累计经营活动现金流量净额，单位人民币元；需与历年相同累计期间比较。 */
    BigDecimal operatingCashflow,

    /** 累计毛利率，以小数存储；例如0.30表示30%，空值表示缺失。 */
    BigDecimal grossMargin,

    /** 累计营业收入同比增速，以小数存储；例如0.20表示20%。 */
    BigDecimal revenueYoy,

    /** 累计归母净利润同比增速，以小数存储；例如-0.10表示下降10%。 */
    BigDecimal profitYoy,

    /** 必填公开来源说明或链接，必须能核查原始披露。 */
    @NotBlank String source) {}
