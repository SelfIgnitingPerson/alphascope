package com.alphascope.portfolio.vo;

import com.alphascope.portfolio.entity.PortfolioAccount;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * 人工资产总额及其可用状态，未知总额不自动推算。
 *
 * @author chenxuefeng
 */
public record AccountView(
    /** 手工登记的投资资产总额，单位人民币元；未知时不能推算持仓集中度。 */
    @JsonProperty("total_assets") BigDecimal totalAssets,

    /** 本次资产或产业指标登记对应的业务日期。 */
    @JsonProperty("as_of_date") String asOfDate,

    /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
    String dataStatus) {
  public static AccountView from(PortfolioAccount row) {
    BigDecimal total = row.getTotalAssets();
    var date = row.getAsOfDate();
    return new AccountView(
        total,
        date == null ? null : date.toString(),
        total == null ? "INSUFFICIENT_DATA" : "COMPLETE");
  }
}
