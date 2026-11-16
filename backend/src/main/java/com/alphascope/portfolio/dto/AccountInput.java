package com.alphascope.portfolio.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 人工登记投资资产总额的请求；仅在显式启用持仓研究时用于集中度计算。
 *
 * @author chenxuefeng
 */
public record AccountInput(
    /** 手工登记的投资资产总额，单位元；该写入请求要求正数，不能以0代替未知。 */
    BigDecimal totalAssets,

    /** 本次资产或产业指标登记对应的业务日期。 */
    @NotNull LocalDate asOfDate) {}
