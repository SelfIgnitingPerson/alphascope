package com.alphascope.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 添加已验证自选与可选首次初始化，日期和动作由用户明确选择。
 *
 * @author chenxuefeng
 * @param symbol 选中的标准A股代码
 * @param initialize 是否立即提交首次研究任务
 * @param date 首次研究业务日，不伪造开市日期
 */
public record WatchDiscoveryInput(
    @NotBlank @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol,
    boolean initialize,
    @NotNull LocalDate date) {}
