package com.alphascope.backtest.service;

import com.alphascope.backtest.dto.ValidationCommand;
import com.alphascope.backtest.vo.ResearchValidationView;

/**
 * 只读核验真实研究数据与回顾规则效果，不触发采集或改变历史报告。
 *
 * @author chenxuefeng
 */
public interface ResearchValidationService {
  /**
   * 核验区间内已有信号、逐日技术规则及完整全A评分的可验证性。
   *
   * @param command 已冻结的起止日期、范围、名单和规则
   * @return 含冻结输入、留出统计、缺项与方法边界的验证报告
   */
  ResearchValidationView validate(ValidationCommand command);
}
