package com.alphascope.fundamental.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 用户明确选择的同行范围；价格、盈利与实收资本由服务端从真实来源读取。
 *
 * @author chenxuefeng
 * @param symbol 可比证券
 * @param parValue 有材料支持的每股面值
 * @param sourceUrl 行业可比性及面值材料
 * @param rationale 同行业、产品和盈利口径的可比性说明；不视为系统行业认证
 */
public record ComparablePeer(
    @NotBlank @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal parValue,
    @NotBlank @Pattern(regexp = "https?://[^\\s]+") @Size(max = 1000) String sourceUrl,
    @NotBlank @Size(min = 20, max = 1000) String rationale) {}
