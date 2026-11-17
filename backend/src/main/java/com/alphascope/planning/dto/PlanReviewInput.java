package com.alphascope.planning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 显式复核日期，不默认取未来日期或盘中未完成收盘。
 *
 * @author chenxuefeng
 * @param date 待核对交易日期
 */
public record PlanReviewInput(@NotNull LocalDate date) {}
