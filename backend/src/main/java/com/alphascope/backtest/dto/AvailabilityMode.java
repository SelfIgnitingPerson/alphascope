package com.alphascope.backtest.dto;

/**
 * 历史统计采用的数据可用时点口径，不能把回填数据冒充当时已知证据。
 *
 * @author chenxuefeng
 */
public enum AvailabilityMode {
  /** 使用当前保存版本回顾历史，明确不证明历史时点可得。 */
  RETROSPECTIVE,
  /** 信号和入场基准行情须在信号日结束前可得，结果行情须在评估截止前可得。 */
  POINT_IN_TIME
}
