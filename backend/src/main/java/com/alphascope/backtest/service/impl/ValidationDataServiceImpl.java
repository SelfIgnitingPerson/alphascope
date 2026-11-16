package com.alphascope.backtest.service.impl;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.ResearchValidationRequest.Scope;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.ValidationCommand;
import com.alphascope.backtest.dto.ValidationDataset;
import com.alphascope.backtest.service.BacktestService;
import com.alphascope.backtest.service.ValidationDataService;
import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.ScoreService;
import com.alphascope.market.service.UniverseCoverageService;
import com.alphascope.market.vo.MarketScoreView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同一快照读取完所需资料即结束事务；不在持有连接时逐日计算或序列化大证据。
 *
 * @author chenxuefeng
 */
@Service
public class ValidationDataServiceImpl implements ValidationDataService {
  private final MarketService market;
  private final ScoreService scores;
  private final UniverseCoverageService coverage;
  private final BacktestService backtest;
  private final AlphaScopeProperties properties;
  private final Clock clock;

  public ValidationDataServiceImpl(
      MarketService market,
      ScoreService scores,
      UniverseCoverageService coverage,
      BacktestService backtest,
      AlphaScopeProperties properties,
      Clock clock) {
    this.market = market;
    this.scores = scores;
    this.coverage = coverage;
    this.backtest = backtest;
    this.properties = properties;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 120)
  public ValidationDataset capture(ValidationCommand command) {
    ExecutionContext.checkpoint();
    var request = command.request();
    var range = DateRangeRequest.builder().start(request.getStart()).end(request.getEnd()).build();
    boolean includeMarket = request.getScope() != Scope.TECHNICAL;
    var score = includeMarket ? scores.research(request.getEnd()) : null;
    var universe = includeMarket ? coverage.inspect(request.getEnd()) : null;
    var history = includeMarket ? scores.history(range) : List.<MarketScoreView>of();
    var qualified =
        history.stream()
            .filter(
                row ->
                    "MARKET_V3".equals(row.modelVersion())
                        && !"UNKNOWN".equals(row.state())
                        && row.score() != null
                        && Boolean.FALSE.equals(row.isSynthetic()))
            .count();
    var benchmark = market.researchBars("000300.SH", range);
    Set<LocalDate> dates;
    try {
      dates =
          market.expectedDates(
              TradingDatesQuery.builder()
                  .symbol("000300.SH")
                  .start(request.getStart())
                  .end(request.getEnd())
                  .build());
    } catch (IllegalStateException e) {
      dates = Set.of();
    }
    var marketInput =
        includeMarket
            ? new MarketScoreValidationCalculator.Input(
                request.getStart(),
                request.getEnd(),
                history,
                benchmark,
                dates,
                properties.getBacktest().getMinimumSamples())
            : null;
    var stocks = new ArrayList<ValidationDataset.Stock>();
    int completed = 0;
    long loadedRows = benchmark.size();
    long generatedSignals = 0;
    for (String symbol : request.getSymbols()) {
      ExecutionContext.checkpoint();
      var saved = backtest.inputs(statistics(symbol, range, AvailabilityMode.RETROSPECTIVE));
      var strict = backtest.inputs(statistics(symbol, range, AvailabilityMode.POINT_IN_TIME));
      var bars = market.researchBars(symbol, range);
      loadedRows += bars.size() + saved.bars().size() + strict.bars().size();
      if (loadedRows > 100000) throw new IllegalArgumentException("验证资料超过十万行，请减少证券或日期区间");
      String failure = null;
      Set<LocalDate> calendar = Set.of(), stockDates = Set.of();
      try {
        LocalDate first =
            bars.isEmpty() || bars.getFirst().getTradeDate().isAfter(request.getStart())
                ? request.getStart()
                : bars.getFirst().getTradeDate();
        calendar =
            market.expectedDates(
                TradingDatesQuery.builder()
                    .symbol("000300.SH")
                    .start(first)
                    .end(request.getEnd())
                    .build());
        stockDates =
            market.expectedDates(
                TradingDatesQuery.builder()
                    .symbol(symbol)
                    .start(first)
                    .end(request.getEnd())
                    .source(bars.isEmpty() ? null : bars.getLast().getSource())
                    .build());
        if (bars.isEmpty()) failure = "没有研究区间内可用的同源真实行情";
      } catch (IllegalStateException e) {
        failure = "权威日历或预热区间缺失：" + e.getMessage();
      }
      generatedSignals +=
          calendar.stream().filter(day -> !day.isBefore(request.getStart())).count() * 5;
      if (generatedSignals > properties.getValidation().getMaximumSignals())
        throw new IllegalArgumentException("逐规则评估超过容量限制，请减少证券或日期区间");
      stocks.add(
          new ValidationDataset.Stock(
              symbol, List.copyOf(bars), benchmark, calendar, stockDates, saved, strict, failure));
      ExecutionContext.progress(
          new ExecutionProgress(
              ++completed, request.getSymbols().size(), "读取验证资料 " + symbol, clock.instant()));
    }
    return new ValidationDataset(
        command, score, universe, marketInput, qualified, List.copyOf(stocks));
  }

  private SignalStatisticsRequest statistics(
      String symbol, DateRangeRequest range, AvailabilityMode mode) {
    return SignalStatisticsRequest.builder()
        .symbol(symbol)
        .start(range.getStart())
        .end(range.getEnd())
        .evaluationDate(range.getEnd())
        .availabilityMode(mode)
        .build();
  }
}
