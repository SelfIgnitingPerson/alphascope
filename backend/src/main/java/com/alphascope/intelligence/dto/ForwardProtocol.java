package com.alphascope.intelligence.dto;

/**
 * 保存时冻结的前瞻试算协议，不在看到未来价格后调整费率或入场规则。
 *
 * @author chenxuefeng
 * @param version 协议版本
 * @param capital 独立试算名义资金，元
 * @param commission 双边佣金比例
 * @param minimumCommission 单边最低佣金，元
 * @param sellStamp 卖出印花税假设
 * @param slippage 单边滑点假设
 * @param maximumParticipation 当日成交额参与上限
 * @param gapLimit 开盘跳空保守排除阈值，非交易所涨跌停规则
 */
public record ForwardProtocol(
    String version,
    double capital,
    double commission,
    double minimumCommission,
    double sellStamp,
    double slippage,
    double maximumParticipation,
    double gapLimit) {
  /**
   * @return 当前冻结协议，旧版本不得被新默认值替换。
   */
  public static ForwardProtocol current() {
    return new ForwardProtocol("FROZEN_FORWARD_V1", 100000, .0003, 5, .0005, .0005, .01, .045);
  }
}
