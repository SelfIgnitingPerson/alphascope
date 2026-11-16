package com.alphascope.portfolio.vo;

import com.alphascope.portfolio.dto.PositionDO;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * 当前手工登记的一只标的持仓。
 *
 * @author chenxuefeng
 */
public record PositionView(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name,

    /** 当前手工持仓数量，单位股；必须非负。 */
    BigDecimal quantity,

    /** 人工登记的每股成本，单位人民币元；必须非负。 */
    @JsonProperty("cost_price") BigDecimal costPrice,

    /** 本次资产或产业指标登记对应的业务日期。 */
    @JsonProperty("as_of_date") String asOfDate) {
  public static PositionView from(PositionDO row) {
    return new PositionView(
        row.getSymbol(),
        row.getName(),
        row.getQuantity(),
        row.getCostPrice(),
        row.getAsOfDate().toString());
  }
}
