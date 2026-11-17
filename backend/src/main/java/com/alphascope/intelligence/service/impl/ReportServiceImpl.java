package com.alphascope.intelligence.service.impl;

import com.alphascope.common.config.ResearchVersion;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.common.vo.CollectionView;
import com.alphascope.fundamental.service.FinancialSyncService;
import com.alphascope.fundamental.service.ForecastService;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.intelligence.builder.RuleReportBuilder;
import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.calculator.ReportAnalysisBuilder;
import com.alphascope.intelligence.dto.ReportQuery;
import com.alphascope.intelligence.dto.ReportResult;
import com.alphascope.intelligence.dto.ReportSearchRequest;
import com.alphascope.intelligence.dto.StockEvidence;
import com.alphascope.intelligence.entity.AnalysisReport;
import com.alphascope.intelligence.mapper.ReportMapper;
import com.alphascope.intelligence.provider.ReportProvider;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.intelligence.vo.ReportAnalysis;
import com.alphascope.intelligence.vo.ReportComparison;
import com.alphascope.intelligence.vo.ReportDetail;
import com.alphascope.intelligence.vo.ReportSummary;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.ScoreService;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.signal.service.SignalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ReportService 的业务实现。
 *
 * <p>报告生成与读取编排：冻结业务时点输入，在事务外生成摘要，最后短事务保存新修订。
 *
 * @author chenxuefeng
 */
@Service
public class ReportServiceImpl implements ReportService {
  private final ReportMapper mapper;
  private final ReportProvider provider;
  private final ScoreService scores;
  private final SignalService signals;
  private final PortfolioService portfolio;
  private final MarketService market;
  private final String model;
  private final FundamentalService financials;
  private final IndicatorService indicators;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final FinancialSyncService financialSync;
  private final IndustryService industry;
  private final ForecastService forecasts;

  private SourceLicenseService licenses;

  @Autowired
  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  public ReportServiceImpl(
      ReportMapper mapper,
      ReportProvider provider,
      ScoreService scores,
      SignalService signals,
      PortfolioService portfolio,
      MarketService market,
      @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
      FundamentalService financials,
      IndicatorService indicators,
      TransactionTemplate tx,
      ObjectMapper json,
      FinancialSyncService financialSync,
      IndustryService industry,
      ForecastService forecasts) {
    this.mapper = mapper;
    this.provider = provider;
    this.scores = scores;
    this.signals = signals;
    this.portfolio = portfolio;
    this.market = market;
    this.model = model;
    this.financials = financials;
    this.indicators = indicators;
    this.tx = tx;
    this.json = json;
    this.financialSync = financialSync;
    this.industry = industry;
    this.forecasts = forecasts;
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<ReportSummary> list(ReportSearchRequest request) {
    String type = request.getType();
    LocalDate end =
        request.getEnd() == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : request.getEnd();
    LocalDate start = request.getStart() == null ? end.minusMonths(3) : request.getStart();
    int page = request.getPage();
    int size = request.getSize();
    boolean latestOnly = request.getLatestOnly();

    request.validate();
    DateRangeValidator.requireValid(start, end);
    if (!Set.of("DAILY", "WEEKLY").contains(type)) {
      throw new IllegalArgumentException("报告类型必须为DAILY或WEEKLY");
    }
    ReportQuery query =
        ReportQuery.builder()
            .reportType(type)
            .startDate(start)
            .endDate(end)
            .size(size)
            .offset((page - 1) * size)
            .synthetic(market.synthetic())
            .latestOnly(latestOnly)
            .build();
    return new PageResponse<>(
        mapper.list(query).stream().map(ReportSummary::from).toList(),
        mapper.count(query),
        page,
        size);
  }

  /** {@inheritDoc} */
  @Override
  public ReportDetail get(long id) {
    var row = mapper.get(id);
    if (row == null) {
      throw new NotFoundException("报告不存在");
    }
    JsonNode snapshot = parse(mapper.snapshot(id));
    var sources =
        mapper.sources(id).stream()
            .map(
                source ->
                    new ReportDetail.Source(
                        source.getSourceDocumentId(), source.getDataReference()))
            .toList();
    return new ReportDetail(
        row.getId(),
        row.getReportType(),
        row.getBusinessDate(),
        row.getRevision(),
        row.getStatus(),
        row.getModel(),
        row.getDataAsOf(),
        row.getCollectedAt(),
        Boolean.TRUE.equals(row.getSynthetic()),
        row.getAlgorithmVersion(),
        row.getDataScope(),
        row.getInputHash(),
        parse(row.getContentJson()),
        sources,
        snapshot,
        readFrozenAnalysis(row, snapshot));
  }

  /** 可选量价研究缺少完整日历时保留缺项，不阻止其他冻结事实的展示。 */
  private List<LocalDate> frozenTradingDates(TradingDatesQuery query) {
    try {
      return market.expectedDates(query).stream().sorted().toList();
    } catch (IllegalStateException e) {
      return List.of();
    }
  }

  /** 新报告直接读取已生成分析；旧报告也只解析旧快照，不能从当前库补造历史。 */
  private ReportAnalysis readFrozenAnalysis(AnalysisReport report, JsonNode snapshot) {
    JsonNode savedAnalysis = snapshot.path("analysis");
    if (savedAnalysis.isObject()) {
      return json.convertValue(savedAnalysis, ReportAnalysis.class);
    }

    boolean synthetic = Boolean.TRUE.equals(report.getSynthetic());
    return ReportAnalysisBuilder.build(
        snapshot, report.getReportType(), report.getBusinessDate(), synthetic);
  }

  private JsonNode parse(Object value) {
    try {
      if (value == null) {
        return json.nullNode();
      }
      if (value instanceof String text) {
        return json.readTree(text);
      }
      return json.valueToTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("报告JSON损坏", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<ReportSummary> versions(long id) {
    var row = get(id);
    return mapper.versions(row.reportType(), row.businessDate(), row.isSynthetic()).stream()
        .map(ReportSummary::from)
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public ReportComparison compare(long left, long right) {
    var a = get(left);
    var b = get(right);
    if (!a.reportType().equals(b.reportType())
        || !a.businessDate().equals(b.businessDate())
        || a.isSynthetic() != b.isSynthetic()) {
      throw new IllegalArgumentException("只能比较同日期同类型同模式的报告");
    }
    var changes = new ArrayList<ReportComparison.Change>();
    for (String field :
        List.of(
            "summary",
            "positiveFactors",
            "negativeFactors",
            "themeOutlook",
            "watchlistRisks",
            "nextChecks",
            "uncertainties")) {
      if (!Objects.equals(a.contentJson().path(field), b.contentJson().path(field))) {
        changes.add(
            new ReportComparison.Change(
                field, a.contentJson().path(field), b.contentJson().path(field)));
      }
    }
    return new ReportComparison(
        left, right, !Objects.equals(a.inputHash(), b.inputHash()), changes);
  }

  /** {@inheritDoc} */
  @Override
  public long generate(String type, LocalDate date) {
    return generate(type, date, null);
  }

  /** {@inheritDoc} */
  @Override
  public long generate(String type, LocalDate date, Long jobId) {
    boolean synthetic = market.synthetic();
    List<StockEvidence> evidence = new ArrayList<>();
    Map<String, CollectionView> financialCollection = new LinkedHashMap<>();
    Set<String> refs = new LinkedHashSet<>();
    LocalDateTime asOf = null, collected = null;
    for (var stock : portfolio.watchlist()) {
      if (!Boolean.TRUE.equals(stock.getEnabled())) {
        continue;
      }
      String symbol = stock.getSymbol();
      var bars =
          market.bars(
              symbol, DateRangeRequest.builder().start(date.minusYears(2)).end(date).build());
      var snapshots =
          indicators.list(symbol, DateRangeRequest.builder().start(date).end(date).build());
      var events =
          signals.list(
              symbol,
              OptionalDateRangeRequest.builder()
                  .start(type.equals("WEEKLY") ? date.with(DayOfWeek.MONDAY) : date)
                  .end(date)
                  .build());
      var published = financials.reports(symbol, date);
      financialCollection.put(symbol, financialSync.status(symbol, date));
      evidence.add(
          new StockEvidence(
              symbol,
              stock.getName(),
              bars,
              snapshots,
              events,
              published,
              market.suspended(symbol, date),
              frozenTradingDates(
                  TradingDatesQuery.builder()
                      .symbol(symbol)
                      .start(date.minusMonths(2))
                      .end(date)
                      .source(bars.isEmpty() ? null : bars.getLast().getSource())
                      .build())));
      for (var bar : bars) {
        refs.add("daily_bar:" + symbol + ":" + bar.getTradeDate() + ":batch:" + bar.getBatchId());
        LocalDateTime fetched = bar.getFetchedAt();
        if (collected == null || fetched.isAfter(collected)) {
          collected = fetched;
        }
      }
      if (!bars.isEmpty()) {
        LocalDateTime last = bars.getLast().getTradeDate().atTime(15, 0);
        if (asOf == null || last.isBefore(asOf)) {
          asOf = last;
        }
      }
      for (var signal : events) {
        refs.add("signal_event:" + signal.getId());
      }
      for (var financial : published) {
        refs.add("financial_report:" + financial.getId());
        LocalDateTime t = financial.getFetchedAt();
        if (t != null) {
          LocalDateTime fetched = t;
          if (collected == null || fetched.isAfter(collected)) {
            collected = fetched;
          }
        }
      }
    }
    if (asOf == null) {
      throw new IllegalStateException("无行情证据，拒绝生成报告");
    }
    var themeContexts = industry.snapshot(date);
    for (var context : themeContexts) {
      var cohort = context.cohort();
      refs.add("theme_research_cohort:" + cohort.getId());
      refs.add("source_document:" + cohort.getSourceDocumentId());
      for (var member : context.members()) {
        for (var bar : member.history()) {
          refs.add(
              "daily_bar:"
                  + member.symbol()
                  + ":"
                  + bar.getTradeDate()
                  + ":batch:"
                  + bar.getBatchId());
          LocalDateTime t = bar.getFetchedAt();
          LocalDateTime fetched = t;
          if (collected == null || fetched.isAfter(collected)) {
            collected = fetched;
          }
        }
      }
    }
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("businessDate", date);
    input.put("priceVolumeMethod", PriceVolumeCalculator.VERSION);
    input.put(
        "benchmark",
        market.bars(
            "000300.SH", DateRangeRequest.builder().start(date.minusYears(2)).end(date).build()));
    input.put("themeContexts", themeContexts);
    Set<String> acquisitionSymbols = new LinkedHashSet<>();
    acquisitionSymbols.add("000300.SH");
    for (var stock : evidence) {
      acquisitionSymbols.add(stock.symbol());
    }
    for (var context : themeContexts) {
      for (var member : context.members()) {
        acquisitionSymbols.add(member.symbol().toString());
      }
    }
    input.put(
        "marketAcquisition",
        acquisitionSymbols.stream()
            .map(symbol -> market.acquisition(symbol, date))
            .filter(Objects::nonNull)
            .toList());
    input.put("ruleParameters", signals.parameters());
    input.put("researchMode", portfolio.positionRiskEnabled() ? "PORTFOLIO" : "WATCHLIST");
    if (portfolio.positionRiskEnabled()) {
      input.put(
          "portfolio", Map.of("positions", portfolio.positions(), "account", portfolio.account()));
    }
    input.put("financialCollection", financialCollection);
    input.put("dataAsOf", asOf);
    var marketOverview = scores.overview(date);
    input.put("market", marketOverview);
    var prediction = forecasts.forReport(date);
    input.put("forecasts", prediction);
    for (var item : prediction) {
      if (item.latest() != null) {
        refs.add("earnings_forecast_snapshot:" + item.latest().id());
        if (collected == null || item.latest().fetchedAt().isAfter(collected)) {
          collected = item.latest().fetchedAt();
        }
      }
    }
    var observation = marketOverview.observation();
    if (observation.snapshotId() != null) {
      refs.add("market_observation_snapshot:" + observation.snapshotId());
      if (collected == null || observation.fetchedAt().isAfter(collected)) {
        collected = observation.fetchedAt();
      }
    }
    var marketSnapshot = marketOverview.snapshot();
    if (marketSnapshot.getSnapshotId() != null) {
      refs.add("market_breadth_snapshot:" + marketSnapshot.getSnapshotId());
    }
    LocalDateTime marketFetched = marketSnapshot.getFetchedAt();
    if (marketFetched != null) {
      LocalDateTime t = marketFetched;
      if (collected == null || t.isAfter(collected)) {
        collected = t;
      }
    }
    input.put("collectedAt", collected);
    input.put("watchlist", evidence);
    input.put("sourceIds", refs);
    input.put("dataCompleteness", "PARTIAL");
    input.put("isSynthetic", synthetic);
    input.put("dataScope", market.dataScope());
    input.put("algorithmVersion", ResearchVersion.ALGORITHM);
    input.put(
        "analysis", ReportAnalysisBuilder.build(json.valueToTree(input), type, date, synthetic));
    var verifiedNarrative =
        RuleReportBuilder.build(type, date, json.valueToTree(input), refs, synthetic);
    input.put("verifiedNarrative", verifiedNarrative);
    if (licenses != null && !synthetic) licenses.requireEvidence(input, "DERIVED_REPORT");
    var generated = synthetic ? Optional.<ReportResult>empty() : provider.summarize(input, refs);
    var result = generated.orElse(verifiedNarrative);
    AnalysisReport row = new AnalysisReport();
    row.setReportType(type);
    row.setBusinessDate(date);
    row.setModel(generated.isPresent() ? model : "RULE_FALLBACK");
    row.setPromptVersion(ResearchVersion.REPORT);
    row.setAlgorithmVersion(ResearchVersion.ALGORITHM);
    row.setInputHash(Jsons.hash(Jsons.write(input)));
    row.setContentJson(Jsons.write(result));
    row.setContentMd(
        "# "
            + date
            + "\n\n"
            + result.summary()
            + "\n\n"
            + String.join("\n", result.watchlistRisks()));
    row.setDataAsOf(asOf);
    row.setCollectedAt(collected);
    row.setSynthetic(synthetic);
    row.setDataScope(market.dataScope());
    row.setJobId(jobId);
    // 模型调用已在事务外结束；只在此短事务中分配修订号并冻结完整输入。
    return tx.execute(
        status -> {
          row.setRevision(mapper.revision(type, date));
          mapper.save(row);
          long id = row.getId();
          mapper.input(id, Jsons.write(input));
          for (String ref : refs) {
            mapper.source(id, ref);
          }
          return id;
        });
  }
}
