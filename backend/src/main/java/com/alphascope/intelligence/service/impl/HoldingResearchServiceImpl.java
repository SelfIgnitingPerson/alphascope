package com.alphascope.intelligence.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.fundamental.service.InvestmentEvidenceService;
import com.alphascope.intelligence.calculator.HoldingResearchCalculator;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.dto.StockEvidence;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.service.ValuationAuditService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.policy.SourceEvidence;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 从已有行情、公开财报和已保存规则组装同一日期证据，不请求上游、不改历史报告。
 *
 * @author chenxuefeng
 */
@Service
public class HoldingResearchServiceImpl implements HoldingResearchService {
  private final MarketService market;

  @Autowired private ValuationAuditService valuationAudit;

  private final FundamentalService financials;
  private final SignalService signals;
  private final SourceLicenseService licenses;

  @Autowired private InvestmentEvidenceService investmentEvidence;

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
            .valuationThesis(
                investmentEvidence == null
                    ? null
                    : investmentEvidence.thesis(new EvidenceQuery(symbol, date)))
            .valuationAudit(
                valuationAudit == null
                    ? null
                    : valuationAudit.read(new EvidenceQuery(symbol, date)))
            .synthetic(market.synthetic())
            .build();
    licenses.requireEvidence(input, "DERIVED_REPORT");
    var recent =
        history.stream()
            .filter(
                v ->
                    v.getTradeDate() != null
                        && !v.getTradeDate().isAfter(date)
                        && v.getAmount() != null
                        && v.getAmount().signum() > 0
                        && !Boolean.TRUE.equals(v.getSynthetic()))
            .sorted(java.util.Comparator.comparing(MarketBarDO::getTradeDate).reversed())
            .limit(20)
            .toList();
    Double turnover20 =
        recent.size() == 20 && recent.stream().map(v -> v.getSource()).distinct().count() == 1
            ? recent.stream().mapToDouble(v -> v.getAmount().doubleValue()).average().orElseThrow()
            : null;
    var facts = new java.util.ArrayList<String>();
    history.stream()
        .sorted(java.util.Comparator.comparing(MarketBarDO::getTradeDate))
        .forEach(
            v ->
                facts.add(
                    v.getTradeDate()
                        + "|"
                        + v.getSource()
                        + "|"
                        + v.getOpen()
                        + "|"
                        + v.getHigh()
                        + "|"
                        + v.getLow()
                        + "|"
                        + v.getClose()
                        + "|"
                        + v.getAmount()
                        + "|"
                        + v.getVolume()
                        + "|"
                        + v.getFactor()));
    stock.financials().stream()
        .sorted(
            java.util.Comparator.comparing(FinancialReport::getPeriodEnd)
                .thenComparing(f -> f.getSource() == null ? "" : f.getSource()))
        .forEach(
            f ->
                facts.add(
                    f.getPeriodEnd()
                        + "|"
                        + f.getSource()
                        + "|"
                        + f.getVersion()
                        + "|"
                        + f.getContentHash()
                        + "|"
                        + f.getRevenue()
                        + "|"
                        + f.getNetProfit()
                        + "|"
                        + f.getOperatingCashflow()
                        + "|"
                        + f.getGrossMargin()));
    var result = HoldingResearchCalculator.calculate(input);
    var quoteFactor =
        history.stream()
            .filter(
                v ->
                    java.util.Objects.equals(v.getTradeDate(), result.quoteDate())
                        && java.util.Objects.equals(v.getSource(), result.source())
                        && v.getFactor() != null)
            .map(v -> v.getFactor().doubleValue())
            .findFirst()
            .orElse(null);
    return result.toBuilder()
        .dataHash(Jsons.hash(Jsons.write(facts)))
        .assumptionHash(Jsons.hash(Jsons.write(input.valuationThesis())))
        .turnover20(turnover20)
        .quoteFactor(quoteFactor)
        .inputSources(SourceEvidence.inspect(input).sources())
        .build();
  }
}
