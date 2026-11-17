package com.alphascope.market.vo;

import com.alphascope.market.entity.Instrument;

/**
 * REST只暴露业务字段，数据库主键留在服务内部。
 *
 * @author chenxuefeng
 */
public record InstrumentView(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name,

    /** 交易所代码，需与symbol的后缀保持一致。 */
    String exchange,

    /** 标的类型：STOCK为股票，INDEX为指数。 */
    String type,

    /** 主数据是否启用；不等同于当日是否交易。 */
    boolean active,
    /** 经来源核验的上市日；空表示仍待核实。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.time.LocalDate listedAt,
    /** 已确认退市日；空不据此推断交易状态。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.time.LocalDate delistedAt) {
  public static InstrumentView from(Instrument instrument) {
    return new InstrumentView(
        instrument.getSymbol(),
        instrument.getName(),
        instrument.getExchange(),
        instrument.getType(),
        instrument.getActive(),
        instrument.getListedAt(),
        instrument.getDelistedAt());
  }
}
