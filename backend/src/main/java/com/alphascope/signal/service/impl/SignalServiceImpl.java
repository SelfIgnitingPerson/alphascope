package com.alphascope.signal.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.indicator.calculator.TechnicalEvidenceCalculator;
import com.alphascope.indicator.dto.TechnicalWindow;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.portfolio.entity.PortfolioAccount;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.signal.dto.RuleCatalog;
import com.alphascope.signal.entity.SignalEvent;
import com.alphascope.signal.entity.SignalRevision;
import com.alphascope.signal.mapper.SignalMapper;
import com.alphascope.signal.rule.RiskRules;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SignalService 的业务实现。
 *
 * <p>整理价格、主题、财报及可选持仓事实后执行纯规则，保存缺失输入和不适用原因。
 *
 * @author chenxuefeng
 */
@Service
public class SignalServiceImpl implements SignalService {
  private final SignalMapper mapper;
  private final RiskRules rules;
  private final MarketService market;
  private final FundamentalService financials;
  private final PortfolioService portfolio;
  private final IndustryService industry;

  public SignalServiceImpl(
      SignalMapper mapper,
      RiskRules rules,
      MarketService market,
      FundamentalService financials,
      PortfolioService portfolio,
      IndustryService industry) {
    this.mapper = mapper;
    this.rules = rules;
    this.market = market;
    this.financials = financials;
    this.portfolio = portfolio;
    this.industry = industry;
  }

  /** {@inheritDoc} */
  @Override
  public RuleCatalog catalog() {
    return rules.catalog(portfolio.positionRiskEnabled());
  }

  /** {@inheritDoc} */
  @Override
  public List<SignalEvent> list(String symbol, OptionalDateRangeRequest request) {
    LocalDate end =
        request.getEnd() == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : request.getEnd();
    LocalDate start = request.getStart() == null ? end.minusMonths(1) : request.getStart();
    DateRangeValidator.requireValid(start, end);

    return mapper.list(
        InstrumentHistoryQuery.builder()
            .symbol(symbol)
            .startDate(start)
            .endDate(end)
            .synthetic(market.synthetic())
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Double> parameters() {
    return rules.parameters();
  }

  private Double number(Number value) {
    return value == null ? null : value.doubleValue();
  }

  private void copy(Map<String, Double> evidence, String key, Number value) {
    if (value != null) {
      evidence.put(key, value.doubleValue());
    }
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void calculate(String symbol, LocalDate date) {
    var bars =
        market.bars(symbol, DateRangeRequest.builder().start(date.minusYears(2)).end(date).build());
    if (bars.isEmpty()) {
      return;
    }
    var last = bars.get(bars.size() - 1);
    if (!date.equals(last.getTradeDate())) throw new IllegalStateException("规则业务日缺少有效收盘");
    var technicalBars = bars.subList(Math.max(0, bars.size() - 61), bars.size());
    Set<LocalDate> expected;
    try {
      expected =
          market.expectedDates(
              TradingDatesQuery.builder()
                  .symbol(symbol)
                  .start(technicalBars.getFirst().getTradeDate())
                  .end(date)
                  .source(last.getSource())
                  .build());
    } catch (IllegalStateException e) {
      expected = Set.of();
    }
    var benchmark =
        market.bars(
            "000300.SH",
            DateRangeRequest.builder()
                .start(technicalBars.getFirst().getTradeDate())
                .end(date)
                .build());
    Map<String, Double> e =
        new HashMap<>(
            TechnicalEvidenceCalculator.calculate(
                new TechnicalWindow(date, technicalBars, benchmark, expected)));
    double close = number(last.getClose());
    var reports = financials.reports(symbol, date);
    if (!reports.isEmpty()) {
      var f = reports.get(0);
      copy(e, "revenueYoy", f.getRevenueYoy());
      copy(e, "profitYoy", f.getProfitYoy());
      copy(e, "grossMargin", f.getGrossMargin());
      copy(e, "cashflow", f.getOperatingCashflow());
      LocalDate period = f.getPeriodEnd();
      for (var older : reports) {
        if (!Objects.equals(f.getReportType(), older.getReportType())) {
          continue;
        }
        LocalDate p = older.getPeriodEnd();
        if (p.equals(period.minusYears(1))) {
          copy(e, "previousYearGrossMargin", older.getGrossMargin());
          copy(e, "previousYearCashflow", older.getOperatingCashflow());
        }
        if (p.equals(period.minusYears(2))) {
          copy(e, "twoYearsAgoCashflow", older.getOperatingCashflow());
        }
      }
    }
    var account = portfolio.positionRiskEnabled() ? portfolio.account() : new PortfolioAccount();
    Double assets = number(account.getTotalAssets());
    if (assets != null && assets > 0 && date.equals(account.getAsOfDate())) {
      for (var position : portfolio.positions()) {
        if (symbol.equals(position.getSymbol()) && date.equals(position.getAsOfDate())) {
          e.put("positionWeight", number(position.getQuantity()) * close / assets);
        }
      }
    }
    var themeEvidence = industry.divergenceEvidence(symbol, date);
    e.putAll(themeEvidence.values());
    boolean synthetic = market.synthetic() || Boolean.TRUE.equals(last.getSynthetic());
    long instrumentId = market.instrument(symbol).getId();
    mapper.lockInstrument(instrumentId);
    for (var result : rules.evaluate(e, synthetic, portfolio.positionRiskEnabled())) {
      SignalEvent row = new SignalEvent();
      row.setInstrumentId(instrumentId);
      row.setSynthetic(synthetic);
      row.setTradeDate(date);
      row.setRuleCode(result.ruleCode());
      row.setRuleVersion(result.ruleVersion());
      row.setSeverity(result.severity());
      row.setStatus(result.status());
      var ruleEvidence =
          new java.util.LinkedHashMap<String, Object>(
              Map.of(
                  "values",
                  result.evidence(),
                  "isSynthetic",
                  synthetic,
                  "applicability",
                  result.status().equals("NOT_APPLICABLE") ? "自选研究模式，不评估个人持仓" : "APPLICABLE",
                  "financialBasis",
                  "披露期累计同比；现金流按同期比较",
                  "reason",
                  result.ruleCode().equals("INDUSTRY_DIVERGENCE") ? themeEvidence.reason() : "",
                  "themeDiagnostics",
                  result.ruleCode().equals("INDUSTRY_DIVERGENCE")
                      ? themeEvidence.themes()
                      : List.of(),
                  "ruleParameters",
                  rules.parameters(),
                  "dataStatus",
                  synthetic ? "SYNTHETIC" : "OBSERVED",
                  "technicalVersion",
                  TechnicalEvidenceCalculator.VERSION,
                  "technicalInputHash",
                  Jsons.hash(Jsons.write(technicalBars))));
      var inputSources = new java.util.TreeSet<String>();
      bars.stream().map(MarketBarDO::getSource).filter(Objects::nonNull).forEach(inputSources::add);
      benchmark.stream()
          .map(MarketBarDO::getSource)
          .filter(Objects::nonNull)
          .forEach(inputSources::add);
      reports.stream()
          .map(FinancialReport::getSource)
          .filter(Objects::nonNull)
          .forEach(inputSources::add);
      ruleEvidence.put("inputSources", inputSources);
      row.setEvidenceJson(Jsons.write(ruleEvidence));
      mapper.save(row);
      var revision = new SignalRevision();
      revision.setInstrumentId(instrumentId);
      revision.setTradeDate(date);
      revision.setRuleCode(row.getRuleCode());
      revision.setRuleVersion(row.getRuleVersion());
      revision.setSeverity(row.getSeverity());
      revision.setStatus(row.getStatus());
      revision.setEvidenceJson(row.getEvidenceJson());
      revision.setParametersJson(Jsons.write(rules.parameters()));
      revision.setSynthetic(synthetic);
      revision.setContentHash(
          Jsons.hash(
              Jsons.write(
                  List.of(
                      row.getRuleVersion(),
                      row.getSeverity(),
                      row.getStatus(),
                      row.getEvidenceJson(),
                      revision.getParametersJson()))));
      if (!revision.getContentHash().equals(mapper.latestRevisionHash(revision)))
        mapper.archive(revision);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<SignalEvent> evaluationRecords(String symbol, DateRangeRequest request) {
    DateRangeValidator.requireValid(request.getStart(), request.getEnd());
    if (request.getStart().plusYears(10).isBefore(request.getEnd())) {
      throw new IllegalArgumentException("研究查询窗口不能超过十年");
    }
    return mapper.evaluationRecords(
        InstrumentHistoryQuery.builder()
            .symbol(symbol)
            .startDate(request.getStart())
            .endDate(request.getEnd())
            .synthetic(market.synthetic())
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public List<SignalEvent> replayRecords(String symbol, DateRangeRequest request) {
    DateRangeValidator.requireValid(request.getStart(), request.getEnd());
    return mapper
        .replay(
            InstrumentHistoryQuery.builder()
                .symbol(symbol)
                .startDate(request.getStart())
                .endDate(request.getEnd())
                .synthetic(market.synthetic())
                .build())
        .stream()
        .map(
            revision -> {
              var row = new SignalEvent();
              row.setId(revision.getId());
              row.setRevisionId(revision.getId());
              row.setInstrumentId(revision.getInstrumentId());
              row.setTradeDate(revision.getTradeDate());
              row.setRuleCode(revision.getRuleCode());
              row.setRuleVersion(revision.getRuleVersion());
              row.setSeverity(revision.getSeverity());
              row.setStatus(revision.getStatus());
              row.setEvidenceJson(revision.getEvidenceJson());
              row.setSynthetic(revision.getSynthetic());
              row.setCreatedAt(revision.getRecordedAt());
              row.setUpdatedAt(revision.getRecordedAt());
              return row;
            })
        .toList();
  }
}
