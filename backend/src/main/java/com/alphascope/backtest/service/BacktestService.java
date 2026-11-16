package com.alphascope.backtest.service;

import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.vo.BacktestView;

/**
 * 只统计已保存触发信号的后续真实收盘，禁止演示统计；不重新生成信号，也不声称是完整策略回测。
 *
 * @author chenxuefeng
 */
public interface BacktestService {

  /**
   * 按已保存触发信号统计后续 5/10/20 个交易日收盘收益，不模拟交易可成交性。
   *
   * @param request 证券代码及必填日期区间
   * @return 按规则和周期分组的统计及方法边界；演示数据被标为禁止生成真实效果统计
   */
  BacktestView statistics(SignalStatisticsRequest request);

  /**
   * 在服务端校验后装配完整只读验证输入，严格模式重建不可变修订。
   *
   * @param request 必填证券及日期、可选实验设置
   * @return 计算所需全部冻结数据，不访问行情上游
   */
  SignalEvaluationInput inputs(SignalStatisticsRequest request);
}
