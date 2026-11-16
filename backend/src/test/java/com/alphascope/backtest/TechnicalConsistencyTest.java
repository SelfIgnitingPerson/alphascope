package com.alphascope.backtest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.backtest.calculator.TechnicalRuleReplayCalculator;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.alphascope.indicator.mapper.IndicatorMapper;
import com.alphascope.indicator.service.impl.IndicatorServiceImpl;
import com.alphascope.industry.dto.DivergenceEvidence;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.service.MarketService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.signal.entity.SignalEvent;
import com.alphascope.signal.mapper.SignalMapper;
import com.alphascope.signal.service.impl.SignalServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 用同一真实格式输入比对生产服务、数据库写入精度和逐日回顾，覆盖临界均线、停牌及预热。
 *
 * @author chenxuefeng
 */
class TechnicalConsistencyTest {
  @Test
  void roundedMeanBoundaryMatchesProductionAndReplayExactly() throws Exception {
    var bars = ResearchValidationFixtures.bars(61);
    for (int i = 0; i < bars.size(); i++) {
      BigDecimal close = new BigDecimal(i < 3 ? "100.0002" : "100.0001");
      bars.get(i).setOpen(close);
      bars.get(i).setHigh(close);
      bars.get(i).setLow(close);
      bars.get(i).setClose(close);
    }
    var dates = new TreeSet<>(bars.stream().map(row -> row.getTradeDate()).toList());
    var end = bars.getLast().getTradeDate();
    var market = mock(MarketService.class);
    var mapper = mock(SignalMapper.class);
    var financials = mock(FundamentalService.class);
    var portfolio = mock(PortfolioService.class);
    var industry = mock(IndustryService.class);
    var instrument = new Instrument();
    instrument.setId(1L);
    when(market.instrument(anyString())).thenReturn(instrument);
    when(market.bars(anyString(), any(DateRangeRequest.class))).thenReturn(bars);
    when(market.expectedDates(any())).thenReturn(dates);
    when(financials.reports(anyString(), any())).thenReturn(List.of());
    when(industry.divergenceEvidence(anyString(), any()))
        .thenReturn(new DivergenceEvidence(Map.of(), List.of(), "fixture"));
    var rules = ResearchValidationFixtures.rules();
    new SignalServiceImpl(mapper, rules, market, financials, portfolio, industry)
        .calculate("300476.SZ", end);
    var saved = ArgumentCaptor.forClass(SignalEvent.class);
    verify(mapper, times(10)).save(saved.capture());
    var production =
        saved.getAllValues().stream()
            .filter(row -> row.getRuleCode().equals("TREND_BREAK_60"))
            .findFirst()
            .orElseThrow();
    var source =
        new SignalEvaluationInput(
            SignalStatisticsRequest.builder().symbol("300476.SZ").start(end).end(end).build(),
            bars,
            bars,
            List.of(),
            dates,
            20);
    var replay =
        TechnicalRuleReplayCalculator.reconstruct(
            new TechnicalReplayInput(source, rules.snapshot(), dates));
    var reconstructed =
        replay.evaluation().signals().stream()
            .filter(row -> row.getRuleCode().equals("TREND_BREAK_60"))
            .findFirst()
            .orElseThrow();
    assertEquals("NOT_TRIGGERED", production.getStatus());
    assertEquals(production.getStatus(), reconstructed.getStatus());
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    assertEquals(
        json.readTree(production.getEvidenceJson()).path("values"),
        json.readTree(reconstructed.getEvidenceJson()).path("values"));
    var indicators = mock(IndicatorMapper.class);
    new IndicatorServiceImpl(indicators, market).calculate("300476.SZ", end);
    var stored = ArgumentCaptor.forClass(IndicatorSnapshot.class);
    verify(indicators).save(stored.capture());
    assertEquals(new BigDecimal("100.0001"), stored.getValue().getMa60());
    assertEquals("ADJ_V2", stored.getValue().getAlgorithmVersion());
  }

  @Test
  void shortTechnicalRulesNeedTheirOwnWindowRatherThanSixtyOneDays() {
    var bars = ResearchValidationFixtures.bars(21);
    var dates = new TreeSet<>(bars.stream().map(row -> row.getTradeDate()).toList());
    var end = bars.getLast().getTradeDate();
    var rules = ResearchValidationFixtures.rules();
    var source =
        new SignalEvaluationInput(
            SignalStatisticsRequest.builder().symbol("300476.SZ").start(end).end(end).build(),
            bars,
            bars,
            List.of(),
            dates,
            20);
    var rows =
        TechnicalRuleReplayCalculator.reconstruct(
                new TechnicalReplayInput(source, rules.snapshot(), dates))
            .evaluation()
            .signals();
    assertEquals(
        "TRIGGERED",
        rows.stream()
            .filter(row -> row.getRuleCode().equals("NEW_LOW_20"))
            .findFirst()
            .orElseThrow()
            .getStatus());
    assertEquals(
        "INSUFFICIENT_DATA",
        rows.stream()
            .filter(row -> row.getRuleCode().equals("TREND_BREAK_60"))
            .findFirst()
            .orElseThrow()
            .getStatus());
  }
}
