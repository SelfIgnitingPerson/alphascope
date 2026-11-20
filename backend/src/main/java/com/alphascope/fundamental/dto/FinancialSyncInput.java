package com.alphascope.fundamental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 管理员显式同步单股三表资料，不因页面读取持续调用来源。
 *
 * @author chenxuefeng
 * @param symbol 标的代码
 * @param date 所选研究日，不能为未来
 */
public record FinancialSyncInput(
    @NotBlank @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol, @NotNull LocalDate date) {}
