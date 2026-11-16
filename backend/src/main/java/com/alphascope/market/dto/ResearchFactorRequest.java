package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;

/**
 * 复权能力请求，指定与截面一致的供应商，禁止把另一通道的因子拼到原价上。
 *
 * @author chenxuefeng
 */
@Builder
public record ResearchFactorRequest(
    /** 标准化市场代码，如sh.600000。 */
    String symbol,
    /** 最早需要覆盖的交易日期。 */
    LocalDate start,
    /** 需要核验到的交易日期。 */
    LocalDate end,
    /** 原价实际来源；必须匹配，不允许单独切换因子来源。 */
    String source) {}
