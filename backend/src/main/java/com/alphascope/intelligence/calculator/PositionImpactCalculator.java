package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.PositionImpact;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 将同日人工持仓与同源研究价匹配，不从持仓登记推测历史交易、风险预算或完整账户。
 *
 * @author chenxuefeng
 */
public final class PositionImpactCalculator {
  private PositionImpactCalculator() {}

  /**
   * 核对已登记持仓影响，缺数据保留未知。
   *
   * @param input 同一账号的持仓、账户和研究
   * @return 有日期边界的影响
   */
  public static PositionImpact calculate(Input input) {
    var p = input.position();
    var v = input.research();
    var a = input.account();
    var checks =
        new ArrayList<>(
            List.of("确认持仓和投资资产已更新到研究日；未登记不等于空仓", "个人用款前提与行业关联敞口须核对；参考损失不包含跳空、流动性或交易费用"));
    if (p == null
        || !Objects.equals(p.getSymbol(), v.symbol())
        || p.getAsOfDate() == null
        || p.getQuantity() == null
        || p.getQuantity().signum() < 0)
      return new PositionImpact("UNKNOWN", null, null, null, null, null, checks);
    var knowledge = input.knowledgeDate() == null ? v.asOf() : input.knowledgeDate();
    boolean currentEstimate = knowledge.isAfter(v.asOf());
    if (p.getAsOfDate().isAfter(knowledge)
        || (!p.getAsOfDate().equals(v.asOf()) && !p.isHistoryVerified() && !currentEstimate)
        || !v.asOf().equals(v.quoteDate())
        || v.close() == null)
      return new PositionImpact("STALE", p.getAsOfDate(), null, null, null, null, checks);
    boolean estimated =
        currentEstimate
            || !p.getAsOfDate().equals(v.asOf())
            || a != null
                && a.isHistoryVerified()
                && a.getAsOfDate() != null
                && a.getAsOfDate().isBefore(v.asOf());
    if (currentEstimate)
      checks.add("按截至" + knowledge + "的当前登记与" + v.quoteDate() + "收盘价估算个人影响；不是研究日当时的历史持仓");
    else if (estimated) checks.add("沿用" + p.getAsOfDate() + "真实登记估算，尚未确认其后是否有交易");
    double qty = p.getQuantity().doubleValue(), value = qty * v.close();
    boolean accountDated =
        a != null
            && a.getAsOfDate() != null
            && !a.getAsOfDate().isAfter(knowledge)
            && (v.asOf().equals(a.getAsOfDate()) || a.isHistoryVerified() || currentEstimate);
    Double weight =
        accountDated && a.getTotalAssets() != null && a.getTotalAssets().signum() > 0
            ? value / a.getTotalAssets().doubleValue()
            : null;
    Double pnl =
        p.getCostPrice() == null ? null : qty * (v.close() - p.getCostPrice().doubleValue());
    Double stop = v.priceVolume() == null ? null : v.priceVolume().stopReference();
    boolean breached =
        stop != null && (v.close() <= stop || "BREACHED".equals(v.priceVolume().stopStatus()));
    String referenceState = stop == null ? "UNKNOWN" : breached ? "BREACHED" : "ABOVE_REFERENCE";
    Double loss = stop == null || breached ? null : qty * (v.close() - stop);
    if (breached && qty > 0)
      checks.add("结构参考已触及或失守，原条件需优先重评；差额不再以0元显示，不能给出未超预算结论。后续损失仍可能扩大，请另看组合压力情景");
    if (accountDated) {
      if (a.getRiskBudget() != null && loss != null)
        checks.add(
            loss > a.getRiskBudget().doubleValue()
                ? "该持仓至结构参考价的差额已超过用户组合风险预算，应优先复核；这不是最大可能损失"
                : "该持仓参考差额未超登记预算，仍须合并其他持仓；不能据此认定风险足够低");
      if (a.getLiquidityReserve() != null)
        checks.add("已登记近期用款预留" + a.getLiquidityReserve().toPlainString() + "元；不把总资产全部当成可加仓资金");
      if (a.getFundsNeededOn() != null)
        checks.add("预计用款日" + a.getFundsNeededOn() + "；请核对持有期限、结算和变现能力，不因技术条件满足延后用款");
      if (a.getFundsPurpose() != null && !a.getFundsPurpose().isBlank())
        checks.add("登记的资金用途：" + a.getFundsPurpose() + "；用款前提优先于观察条件");
    }
    Double liquidityDays =
        v.turnover20() != null && v.turnover20() > 0
            ? Math.ceil(value / (v.turnover20() * .01))
            : null;
    if (liquidityDays != null && liquidityDays > 5)
      checks.add("按20个有效交易日日均成交额1%估算需要" + liquidityDays.longValue() + "个交易日退出；不含停牌跌停，不保证实际可成交");
    return new PositionImpact(
        qty > 0 ? (estimated ? "ESTIMATED" : "HELD") : "FLAT",
        p.getAsOfDate(),
        value,
        weight,
        pnl,
        loss,
        checks,
        referenceState,
        liquidityDays);
  }

  /**
   * 同一身份读取的输入，账户可未知。
   *
   * @author chenxuefeng
   * @param position 当前持仓或空
   * @param account 账户或空
   * @param research 当日研究
   */
  public record Input(
      PositionDO position,
      PortfolioAccount account,
      HoldingResearchView research,
      java.time.LocalDate knowledgeDate) {
    /** 默认按严格研究日核对，未来持仓不进入过去。 */
    public Input(PositionDO position, PortfolioAccount account, HoldingResearchView research) {
      this(position, account, research, research.asOf());
    }
  }
}
