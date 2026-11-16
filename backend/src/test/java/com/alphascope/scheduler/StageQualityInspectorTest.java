package com.alphascope.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.task.PipelineStage;
import com.alphascope.common.vo.CollectionView;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.fundamental.service.*;
import com.alphascope.fundamental.vo.ForecastView;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.market.service.*;
import com.alphascope.market.vo.*;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.dto.DataQualityStatus;
import com.alphascope.scheduler.quality.StageQualityInspector;
import com.alphascope.signal.entity.SignalEvent;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 可选采集失败、历史时点不足及规则未触发分别表达，不因为动作正常返回就显示数据完整。
 *
 * @author chenxuefeng
 */
class StageQualityInspectorTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);
  private final PortfolioService portfolio = mock(PortfolioService.class);
  private final MarketService market = mock(MarketService.class);
  private final ForecastService forecasts = mock(ForecastService.class);
  private final FundamentalService financials = mock(FundamentalService.class);
  private final FinancialSyncService sync = mock(FinancialSyncService.class);
  private final MarketBreadthService breadth = mock(MarketBreadthService.class);
  private final MarketObservationService observations = mock(MarketObservationService.class);
  private final IndicatorService indicators = mock(IndicatorService.class);
  private final SignalService signals = mock(SignalService.class);
  private final StageQualityInspector inspector =
      new StageQualityInspector(
          portfolio,
          market,
          forecasts,
          financials,
          sync,
          breadth,
          observations,
          indicators,
          signals);

  StageQualityInspectorTest() {
    var stock = new WatchlistDO();
    stock.setSymbol("300476.SZ");
    stock.setEnabled(true);
    when(portfolio.watchlist()).thenReturn(List.of(stock));
  }

  @Test
  void syntheticModeDoesNotPretendToHaveRealResearchEvidence() {
    when(market.synthetic()).thenReturn(true);
    assertEquals(
        DataQualityStatus.NOT_APPLICABLE,
        inspector.inspect(PipelineStage.RESEARCH_DATA, date).status());
    verifyNoInteractions(financials, breadth);
  }

  @Test
  void currentForecastCollectionCannotFillAnEarlierReportsAvailability() {
    var forecast = mock(ForecastView.class);
    when(forecast.status()).thenReturn("NOT_OBSERVED_AS_OF");
    when(forecast.symbol()).thenReturn("300476.SZ");
    when(forecasts.forReport(date)).thenReturn(List.of(forecast));
    assertEquals(
        DataQualityStatus.UNAVAILABLE, inspector.inspect(PipelineStage.FORECASTS, date).status());
  }

  @Test
  void failedRefreshCanReuseEvidenceButMustExposeTheRefreshFailure() {
    when(financials.reports("300476.SZ", date)).thenReturn(List.of(new FinancialReport()));
    when(sync.status("300476.SZ", date))
        .thenReturn(CollectionView.state("UNAVAILABLE", "refresh failed"));
    var snapshot = new MarketBreadthView();
    snapshot.setBusinessDate(date);
    snapshot.setSource("BAOSTOCK");
    snapshot.setStatus("COMPLETE");
    when(breadth.snapshot(date)).thenReturn(snapshot);
    var quality = inspector.inspect(PipelineStage.RESEARCH_DATA, date);
    assertEquals(DataQualityStatus.PARTIAL, quality.status());
    assertEquals(2, quality.available());
    assertEquals("CACHE_USED", quality.issues().getFirst().status());
  }

  @Test
  void anEarlierObservationCannotQualifyAsTheRequestedDate() {
    var analysis = mock(MarketObservation.class);
    when(analysis.businessDate()).thenReturn(date.minusDays(1));
    when(observations.view(date))
        .thenReturn(
            new MarketObservationView("AVAILABLE", 1L, null, analysis, CollectionView.empty()));
    assertEquals(
        DataQualityStatus.UNAVAILABLE,
        inspector.inspect(PipelineStage.MARKET_OBSERVATION, date).status());
  }

  @Test
  void insufficientIndicatorsStayInsufficientAfterTheStageRuns() {
    var row = new IndicatorSnapshot();
    row.setDataStatus("INSUFFICIENT_HISTORY");
    when(indicators.list(eq("300476.SZ"), any())).thenReturn(List.of(row));
    assertEquals(
        DataQualityStatus.UNAVAILABLE, inspector.inspect(PipelineStage.INDICATORS, date).status());
  }

  @Test
  void confirmedSuspensionDoesNotBecomeMissingRulesOrIndicators() {
    when(market.dayStatus("300476.SZ", date)).thenReturn("SUSPENDED");
    assertEquals(
        DataQualityStatus.NOT_APPLICABLE,
        inspector.inspect(PipelineStage.SCORE_RULES, date).status());
    assertEquals(
        DataQualityStatus.NOT_APPLICABLE,
        inspector.inspect(PipelineStage.INDICATORS, date).status());
    verifyNoInteractions(signals, indicators);
  }

  @Test
  void untriggeredIsAvailableEvidenceAndNotApplicableDoesNotInflateMissingCount() {
    var catalog =
        new com.alphascope.signal.dto.RuleCatalog(
            "ACTIVE",
            List.of(
                new com.alphascope.signal.dto.RuleCatalog.Definition("A", true),
                new com.alphascope.signal.dto.RuleCatalog.Definition("B", false)));
    when(signals.catalog()).thenReturn(catalog);
    var observed = new SignalEvent();
    observed.setRuleCode("A");
    observed.setRuleVersion("ACTIVE");
    observed.setStatus("NOT_TRIGGERED");
    var ignored = new SignalEvent();
    ignored.setRuleCode("B");
    ignored.setRuleVersion("ACTIVE");
    ignored.setStatus("NOT_APPLICABLE");
    when(signals.list(eq("300476.SZ"), any())).thenReturn(List.of(observed, ignored));
    var quality = inspector.inspect(PipelineStage.SCORE_RULES, date);
    assertEquals(DataQualityStatus.AVAILABLE, quality.status());
    assertEquals(1, quality.expected());
  }

  @Test
  void oneMissingApplicableRuleCannotShrinkTheDenominator() {
    var rows = allRules();
    rows.removeIf(row -> row.getRuleCode().equals("INDUSTRY_DIVERGENCE"));
    when(signals.list(anyString(), any())).thenReturn(rows);
    var value = inspector.inspect(PipelineStage.SCORE_RULES, date);
    assertEquals(9, value.expected());
    assertEquals(8, value.available());
    assertEquals(DataQualityStatus.PARTIAL, value.status());
    assertTrue(value.issues().stream().anyMatch(issue -> issue.status().equals("MISSING_RULE")));
  }

  @Test
  void oldRuleVersionDoesNotSatisfyTheCurrentCatalog() {
    var rows = allRules();
    rows.forEach(row -> row.setRuleVersion("OLD"));
    when(signals.list(anyString(), any())).thenReturn(rows);
    var value = inspector.inspect(PipelineStage.SCORE_RULES, date);
    assertEquals(9, value.expected());
    assertEquals(0, value.available());
    assertEquals(DataQualityStatus.UNAVAILABLE, value.status());
  }

  @Test
  void duplicateAndWrongApplicabilityAreDiagnosed() {
    var rows = allRules();
    rows.add(rows.getFirst());
    rows.stream()
        .filter(row -> row.getRuleCode().equals("PROFIT_SLOWDOWN"))
        .forEach(row -> row.setStatus("NOT_APPLICABLE"));
    when(signals.list(anyString(), any())).thenReturn(rows);
    var value = inspector.inspect(PipelineStage.SCORE_RULES, date);
    assertEquals(9, value.expected());
    assertEquals(7, value.available());
    assertTrue(value.issues().stream().anyMatch(issue -> issue.status().equals("DUPLICATE_RULE")));
    assertTrue(
        value.issues().stream().anyMatch(issue -> issue.scope().contains("PROFIT_SLOWDOWN")));
  }

  @Test
  void missingNotApplicableRecordIsNotSilentlyIgnored() {
    var rows = allRules();
    rows.removeIf(row -> row.getRuleCode().equals("POSITION_CONCENTRATION"));
    when(signals.list(anyString(), any())).thenReturn(rows);
    var value = inspector.inspect(PipelineStage.SCORE_RULES, date);
    assertEquals(9, value.available());
    assertEquals(DataQualityStatus.PARTIAL, value.status());
  }

  @Test
  void oldExtraVersionDoesNotDoubleCountValidCurrentResults() {
    var rows = allRules();
    var old = new SignalEvent();
    old.setRuleCode(rows.getFirst().getRuleCode());
    old.setRuleVersion("OLD");
    old.setStatus("INSUFFICIENT_DATA");
    rows.add(old);
    when(signals.list(anyString(), any())).thenReturn(rows);
    var value = inspector.inspect(PipelineStage.SCORE_RULES, date);
    assertEquals(9, value.available());
    assertEquals(9, value.expected());
    assertEquals(DataQualityStatus.AVAILABLE, value.status());
  }

  private java.util.ArrayList<SignalEvent> allRules() {
    var catalog =
        new com.alphascope.signal.rule.RiskRules(-.03, 1.5, -.10, .20, .05, .03, .10)
            .catalog(false);
    when(signals.catalog()).thenReturn(catalog);
    var rows = new java.util.ArrayList<SignalEvent>();
    for (var definition : catalog.definitions()) {
      var row = new SignalEvent();
      row.setRuleCode(definition.code());
      row.setRuleVersion(catalog.version());
      row.setStatus(definition.applicable() ? "NOT_TRIGGERED" : "NOT_APPLICABLE");
      rows.add(row);
    }
    return rows;
  }
}
