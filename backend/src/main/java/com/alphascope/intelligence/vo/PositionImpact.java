package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 人工持仓对研究优先级的影响，仅同日完整登记才计算敞口，不把缺登记当空仓。
 *
 * @author chenxuefeng
 * @param state HELD、FLAT、STALE或UNKNOWN
 * @param recordedAt 持仓登记日
 * @param marketValue 同日持仓市值，元
 * @param weight 占同日投资资产比例
 * @param unrealizedPnl 未实现盈亏，元
 * @param referenceLoss 到结构参考线的静态价格差金额，非最大损失或成交保证
 * @param checks 个人风险仍需确认的条件
 */
public record PositionImpact(
    String state,
    LocalDate recordedAt,
    Double marketValue,
    Double weight,
    Double unrealizedPnl,
    Double referenceLoss,
    List<String> checks,
    /** ABOVE_REFERENCE、BREACHED或UNKNOWN；触及风险线时金额不能以零表示安全。 */
    String referenceState,
    /** 按20个有效日成交额1%估算退出交易日数；不含停牌跌停，非成交保证。 */
    Double liquidityDays) {
  /** 保留旧记录及未知持仓构造，缺省不倒填风险线状态。 */
  public PositionImpact(
      String state,
      LocalDate recordedAt,
      Double marketValue,
      Double weight,
      Double unrealizedPnl,
      Double referenceLoss,
      List<String> checks) {
    this(
        state,
        recordedAt,
        marketValue,
        weight,
        unrealizedPnl,
        referenceLoss,
        checks,
        "UNKNOWN",
        null);
  }
}
