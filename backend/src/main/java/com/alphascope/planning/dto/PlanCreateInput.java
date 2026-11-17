package com.alphascope.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 冻结服务端实际研究依据；不接受客户端价位或用户归属。
 *
 * @author chenxuefeng
 * @param symbol 证券代码
 * @param date 研究日期
 * @param horizon SHORT、MEDIUM或LONG
 * @param actionCode 当期真实存在的行动方案代码
 */
public record PlanCreateInput(
    @NotBlank String symbol,
    @NotNull LocalDate date,
    @Pattern(regexp = "SHORT|MEDIUM|LONG") @NotBlank String horizon,
    @NotBlank String actionCode) {}
