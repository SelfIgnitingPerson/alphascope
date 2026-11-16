package com.alphascope.backtest.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 价格到技术指标到规则的逐层输出；每层单独冻结并核对。
 *
 * @author chenxuefeng
 */
public record TechnicalReplayResult(
    /** 已经逐日重建信号的统计输入。 */
    SignalEvaluationInput evaluation,
    /** 每日按生产精度形成的全部技术证据。 */
    List<EvidenceRow> indicators) {
  /**
   * 一个业务日的技术证据，不包含当日之后的行情。
   *
   * @author chenxuefeng
   */
  public record EvidenceRow(
      /** 业务日期。 */
      LocalDate date,
      /** 已统一精度的指标与缺项；缺失值不填0。 */
      Map<String, Double> values) {}
}
