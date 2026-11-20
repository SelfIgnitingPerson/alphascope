package com.alphascope.fundamental.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 从公开财报核对的股本依据，系统还须对照实际保存的利润、EPS和实收资本。
 *
 * @author chenxuefeng
 * @param currentShares 当前总股数，非万股
 * @param annualWeightedShares 最近年度加权平均股数
 * @param parValue 每股面值，元，不能默认所有股票均为1元
 * @param sourceUrl 对应股本及面值公开材料
 */
public record ValuationShareBasis(
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal currentShares,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal annualWeightedShares,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal parValue,
    @NotBlank @Pattern(regexp = "https?://[^\\s]+") @Size(max = 1000) String sourceUrl) {}
