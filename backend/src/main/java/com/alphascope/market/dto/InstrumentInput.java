package com.alphascope.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 创建证券标的主数据的请求，代码后缀必须与交易所一致。
 *
 * @author chenxuefeng
 */
public record InstrumentInput(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    @NotBlank @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol,

    /** 标的中文名称，用于页面展示。 */
    @NotBlank String name,

    /** 交易所代码，需与symbol的后缀保持一致。 */
    @NotBlank @Pattern(regexp = "SH|SZ|BJ") String exchange,

    /** 标的类型：STOCK为股票，INDEX为指数。 */
    @NotBlank @Pattern(regexp = "STOCK|INDEX") String type,

    /** 已知上市日期；允许为空，未知时不得伪造上市前证据。 */
    LocalDate listedAt) {}
