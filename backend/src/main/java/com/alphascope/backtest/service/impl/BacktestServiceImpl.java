package com.alphascope.backtest.service.impl;

import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.service.BacktestService;
import com.alphascope.backtest.vo.BacktestView;
import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.signal.service.SignalService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BacktestService 的业务实现。
 *
 * <p>只统计已保存触发信号的后续真实收盘，禁止演示统计；不重新生成信号，也不声称是完整策略回测。
 *
 * @author chenxuefeng
 */
@Service
public class BacktestServiceImpl implements BacktestService {
  private final MarketService market;
  private final SignalService signals;
  private final AlphaScopeProperties properties;
  private final Clock clock;

  public BacktestServiceImpl(
      MarketService market, SignalService signals, AlphaScopeProperties properties, Clock clock) {
    this.market = market;
    this.signals = signals;
    this.properties = properties;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BacktestView statistics(SignalStatisticsRequest request) {
    if (market.synthetic()) {
      return new BacktestView("SYNTHETIC", "模拟数据禁止生成真实信号效果统计", Map.of(), List.of());
    }
    return SignalEvaluationCalculator.evaluate(inputs(request));
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SignalEvaluationInput inputs(SignalStatisticsRequest request) {
    DateRangeValidator.requireValid(request.getStart(), request.getEnd());
    LocalDate evaluation =
        request.getEvaluationDate() == null ? request.getEnd() : request.getEvaluationDate();
    if (evaluation.isBefore(request.getEnd())
        || evaluation.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))))
        || request.getStart().plusYears(10).isBefore(evaluation)) {
      throw new IllegalArgumentException("评估截止须不早于信号结束日期、不晚于上海当前日，且总窗口不超过十年");
    }
    if (market.synthetic()) {
      throw new IllegalArgumentException("演示模式不能保存真实验证实验");
    }
    var range = DateRangeRequest.builder().start(request.getStart()).end(evaluation).build();
    Set<LocalDate> calendar;
    try {
      calendar =
          request.getAvailabilityMode() == AvailabilityMode.POINT_IN_TIME
              ? market.replayCalendar(range)
              : market.expectedDates(
                  TradingDatesQuery.builder()
                      .symbol("000300.SH")
                      .start(request.getStart())
                      .end(evaluation)
                      .source(null)
                      .build());
    } catch (IllegalStateException e) {
      calendar = Set.of();
    }
    boolean strict = request.getAvailabilityMode() == AvailabilityMode.POINT_IN_TIME;
    return SignalEvaluationInput.builder()
        .request(request)
        .bars(
            strict
                ? market.replayBars(request.getSymbol(), range)
                : market.bars(request.getSymbol(), range))
        .benchmark(strict ? market.replayBars("000300.SH", range) : market.bars("000300.SH", range))
        .signals(
            strict
                ? signals.replayRecords(
                    request.getSymbol(),
                    DateRangeRequest.builder()
                        .start(request.getStart())
                        .end(request.getEnd())
                        .build())
                : signals.evaluationRecords(
                    request.getSymbol(),
                    DateRangeRequest.builder()
                        .start(request.getStart())
                        .end(request.getEnd())
                        .build()))
        .tradingDates(calendar)
        .minimumSamples(properties.getBacktest().getMinimumSamples())
        .build();
  }
}
