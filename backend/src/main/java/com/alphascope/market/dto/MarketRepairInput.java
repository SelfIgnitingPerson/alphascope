package com.alphascope.market.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 显式核验本机已存历史，禁止用今日目录填补过去。
 *
 * @author chenxuefeng
 * @param asOf 研究截止日
 * @param dates 最多10个明确待核验日期
 */
public record MarketRepairInput(
    @NotNull LocalDate asOf, @NotNull @Size(min = 1, max = 10) List<@NotNull LocalDate> dates) {}
