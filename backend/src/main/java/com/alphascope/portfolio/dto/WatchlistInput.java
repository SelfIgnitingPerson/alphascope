package com.alphascope.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 添加或更新关注标的的请求，不表示建立实际持仓。
 *
 * @author chenxuefeng
 */
public record WatchlistInput(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    @NotBlank String symbol,

    /** 研究或排序的优先级，具体取值由该结构的业务说明确定。 */
    int priority) {}
