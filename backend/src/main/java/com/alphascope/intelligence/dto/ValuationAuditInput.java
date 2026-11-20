package com.alphascope.intelligence.dto;

import com.alphascope.fundamental.dto.ValuationThesisInput;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.List;

/**
 * 独立估值的纯计算输入，材料均为截止日已保存的真实版本。
 *
 * @author chenxuefeng
 * @param date 截止日
 * @param thesis 用户情景
 * @param reports 标的财报
 * @param peers 明确同行范围的实际材料
 */
public record ValuationAuditInput(
    LocalDate date,
    ValuationThesisInput thesis,
    List<FinancialReport> reports,
    List<PeerInput> peers) {
  /**
   * 同行真实财报与同日行情，股本面值假设另来自用户提供材料。
   *
   * @author chenxuefeng
   * @param symbol 标的
   * @param reports 截止日前可用财报
   * @param quote 同日未复权行情，可为空
   */
  public record PeerInput(String symbol, List<FinancialReport> reports, MarketBarDO quote) {}
}
