package com.alphascope.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 管理员依据公开原文补全生命周期，不能改写股票代码映射。
 *
 * @author chenxuefeng
 * @param listedAt 已核验上市日期
 * @param delistedAt 明确退市日期，可空
 * @param evidenceUrl 公开HTTPS依据
 * @param reference 条款或公告定位
 */
public record LifecycleInput(
    @NotNull LocalDate listedAt,
    LocalDate delistedAt,
    @NotBlank @Size(max = 1000) String evidenceUrl,
    @NotBlank @Size(max = 200) String reference) {}
