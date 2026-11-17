package com.alphascope.intelligence.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.intelligence.calculator.HoldingResearchCalculator;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.dto.StockEvidence;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.policy.SourceEvidence;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 从已有行情、公开财报和已保存规则组装同一日期证据，不请求上游、不改历史报告。
 *
 * @author chenxuefeng
 */
@Service
public class HoldingResearchServiceImpl implements HoldingResearchService {
  private final MarketService market;
  private final FundamentalService financials;
  private final SignalService signals;
  private final SourceLicenseService licenses;

  public HoldingResearchServiceImpl(
      MarketService market,
      FundamentalService financials,
      SignalService signals,
      SourceLicenseService licenses) {
    this.market = market;
    this.financials = financials;
    this.signals = signals;
    this.licenses = licenses;
  }

  /** {@inheritDoc} */
  @Override
  public HoldingResearchView read(String symbol, HoldingResearchQuery query) {
    LocalDate date = query.getAsOf();
    if (date == null
        || date.getYear() < 1990
        || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) {
      throw new IllegalArgumentException("研究日期须在1990年至上海当前日期之间");
    }
    var instrument = market.instrument(symbol);
    var history =
        market.bars(symbol, DateRangeRequest.builder().start(date.minusYears(2)).end(date).build());
    Set<LocalDate> expected;
    try {
      expected =
          market.expectedDates(
              TradingDatesQuery.builder()
                  .symbol(symbol)
                  .start(date.minusYears(2))
                  .end(date)
                  .source(history.isEmpty() ? null : history.getLast().getSource())
                  .build());
    } catch (IllegalStateException missingCalendar) {
      expected = Set.of();
    }
    var stock =
        StockEvidence.builder()
            .symbol(symbol)
            .name(instrument.getName())
            .history(history)
            .indicators(java.util.List.of())
            .signals(
                signals.list(
                    symbol, OptionalDateRangeRequest.builder().start(date).end(date).build()))
            .financials(financials.reports(symbol, date))
            .suspended(market.suspended(symbol, date))
            .expectedTradingDates(
                expected.stream().filter(d -> !d.isBefore(date.minusMonths(2))).sorted().toList())
            .build();
    var input =
        HoldingResearchInput.builder()
            .stock(stock)
            .asOf(date)
            .expectedDates(expected)
            .synthetic(market.synthetic())
            .build();
    licenses.requireEvidence(input, "DERIVED_REPORT");
    return HoldingResearchCalculator.calculate(input).toBuilder()
        .inputSources(SourceEvidence.inspect(input).sources())
        .build();
  }
}
