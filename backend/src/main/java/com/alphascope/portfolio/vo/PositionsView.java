package com.alphascope.portfolio.vo;

import java.util.List;

/**
 * 当前人工持仓、资产总额和统一币种的响应。
 *
 * @author chenxuefeng
 */
public record PositionsView(
    /** 当前页或当前响应包含的业务记录列表。 */
    List<PositionView> items,

    /** 人工登记的资产总额及数据状态；未知时不推算集中度。 */
    AccountView account,

    /** 资产货币标识，当前固定CNY。 */
    String currency) {}
