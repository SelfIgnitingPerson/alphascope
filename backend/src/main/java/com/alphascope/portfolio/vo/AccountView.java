package com.alphascope.portfolio.vo;

import com.alphascope.portfolio.entity.PortfolioAccount;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

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
    String dataStatus,
    /** 用户自行选择的组合风险预算，元。 */
    @JsonProperty("risk_budget") BigDecimal riskBudget,
    /** 近期预留的用款金额，元。 */
    @JsonProperty("liquidity_reserve") BigDecimal liquidityReserve,
    /** 预计用款日。 */
    @JsonProperty("funds_needed_on") LocalDate fundsNeededOn,
    /** 资金用途，当前身份私有。 */
    @JsonProperty("funds_purpose") String fundsPurpose,
    /** 人工登记现金余额。 */
    @JsonProperty("cash_balance") BigDecimal cashBalance,
    /** 用户确认完整登记，未知留空。 */
    @JsonProperty("positions_complete") Boolean positionsComplete) {
  public static AccountView from(PortfolioAccount row) {
    BigDecimal total = row.getTotalAssets();
    var date = row.getAsOfDate();
    return new AccountView(
        total,
        date == null ? null : date.toString(),
        total == null ? "INSUFFICIENT_DATA" : "COMPLETE",
        row.getRiskBudget(),
        row.getLiquidityReserve(),
        row.getFundsNeededOn(),
        row.getFundsPurpose(),
        row.getCashBalance(),
        row.getPositionsComplete());
  }
}
