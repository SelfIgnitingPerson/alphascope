package com.alphascope.backtest.controller;

import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.service.BacktestService;
import com.alphascope.backtest.vo.BacktestView;
import com.alphascope.common.response.Result;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询已保存信号的后续收盘收益统计；不模拟下单、滑点或可成交性。
 *
 * @author chenxuefeng
 */
@RestController
public class BacktestController {
  private final BacktestService service;

  public BacktestController(BacktestService service) {
    this.service = service;
  }

  /**
   * 按证券及日期对象查询已保存信号的后续收益统计。
   *
   * @param request 必填证券代码 symbol 及日期 start、end
   * @return 统一响应中的收益统计及方法边界
   */
  @GetMapping("/api/v1/backtest/signals")
  public Result<BacktestView> signals(
      @Valid @ModelAttribute @ParameterObject SignalStatisticsRequest request) {
    return Result.success(service.statistics(request));
  }
}
