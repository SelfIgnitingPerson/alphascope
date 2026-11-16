package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alphascope.backtest.controller.BacktestController;
import com.alphascope.backtest.service.BacktestService;
import com.alphascope.common.config.RequestFilter;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.ApiExceptionHandler;
import com.alphascope.common.response.PageResponse;
import com.alphascope.indicator.controller.IndicatorController;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.intelligence.controller.ReportController;
import com.alphascope.intelligence.dto.ReportSearchRequest;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.market.controller.InstrumentController;
import com.alphascope.market.dto.InstrumentSearchRequest;
import com.alphascope.market.service.MarketService;
import com.alphascope.scheduler.controller.JobController;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.signal.controller.SignalController;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET 请求对象的原参数兼容、字段校验、缺省值及 URL 标识隔离验证。
 *
 * @author chenxuefeng
 */
class QueryRequestContractTest {
  private final IndicatorService indicators = mock(IndicatorService.class);
  private final MarketService market = mock(MarketService.class);
  private final ReportService reports = mock(ReportService.class);
  private final SignalService signals = mock(SignalService.class);
  private final BacktestService statistics = mock(BacktestService.class);
  private final JobService jobs = mock(JobService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(market.instruments(any()))
        .thenAnswer(
            call -> {
              InstrumentSearchRequest request = call.getArgument(0);
              return new PageResponse<>(List.of(), 0, request.getPage(), request.getSize());
            });
    when(reports.list(any()))
        .thenAnswer(
            call -> {
              ReportSearchRequest request = call.getArgument(0);
              return new PageResponse<>(List.of(), 0, request.getPage(), request.getSize());
            });
    when(jobs.page(any()))
        .thenAnswer(
            call -> {
              PageRequest request = call.getArgument(0);
              return new PageResponse<>(List.of(), 0, request.getPage(), request.getSize());
            });
    mvc =
        MockMvcBuilders.standaloneSetup(
                new IndicatorController(indicators),
                new InstrumentController(market),
                new ReportController(reports),
                new SignalController(signals),
                new BacktestController(statistics),
                new JobController(jobs))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestFilter())
            .build();
  }

  @Test
  void originalIndicatorParametersBindAsOneObjectAndPathIdentityWins() throws Exception {
    mvc.perform(
            get("/api/v1/instruments/300476.SZ/indicators")
                .param("start", "2026-10-01")
                .param("end", "2026-10-30")
                .param("symbol", "999999.SH")
                .param("synthetic", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data").isArray());
    var query = ArgumentCaptor.forClass(DateRangeRequest.class);
    verify(indicators).list(eq("300476.SZ"), query.capture());
    assertEquals(LocalDate.of(2026, 10, 1), query.getValue().getStart());
    assertEquals(LocalDate.of(2026, 10, 30), query.getValue().getEnd());
  }

  @Test
  void missingRequiredDateReturns400BeforeCallingService() throws Exception {
    mvc.perform(get("/api/v1/instruments/300476.SZ/indicators").param("end", "2026-10-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.requestId").isString());
    verifyNoInteractions(indicators);
  }

  @Test
  void invalidDateFormatReturns400InsteadOfInternalError() throws Exception {
    mvc.perform(
            get("/api/v1/instruments/300476.SZ/indicators")
                .param("start", "bad-date")
                .param("end", "2026-10-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    verifyNoInteractions(indicators);
  }

  @Test
  void reversedRequiredDatesAreRejectedBeforeCallingService() throws Exception {
    mvc.perform(
            get("/api/v1/instruments/300476.SZ/bars")
                .param("start", "2026-10-31")
                .param("end", "2026-10-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    verify(market, never()).bars(anyString(), any());
  }

  @Test
  void instrumentSearchKeepsExistingDefaults() throws Exception {
    mvc.perform(get("/api/v1/instruments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(20));
    var query = ArgumentCaptor.forClass(InstrumentSearchRequest.class);
    verify(market).instruments(query.capture());
    assertEquals("", query.getValue().getQ());
    assertEquals(1, query.getValue().getPage());
    assertEquals(20, query.getValue().getSize());
  }

  @Test
  void instrumentSearchBindsKeywordsAndExplicitPaging() throws Exception {
    mvc.perform(get("/api/v1/instruments").param("q", "胜宏").param("page", "2").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2));
    verify(market)
        .instruments(
            argThat(
                request ->
                    request.getQ().equals("胜宏")
                        && request.getPage() == 2
                        && request.getSize() == 5));
  }

  @Test
  void emptyReportDefaultParametersRemainCompatible() throws Exception {
    mvc.perform(
            get("/api/v1/reports")
                .param("type", "")
                .param("page", "")
                .param("size", "")
                .param("latestOnly", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(20));
    verify(reports)
        .list(
            argThat(
                request ->
                    request.getType().equals("DAILY")
                        && request.getPage() == 1
                        && request.getSize() == 20
                        && request.getLatestOnly()));
  }

  @Test
  void explicitReportFiltersAndFalseBooleanRemainAvailable() throws Exception {
    mvc.perform(
            get("/api/v1/reports")
                .param("type", "WEEKLY")
                .param("start", "2026-10-01")
                .param("end", "2026-10-30")
                .param("latestOnly", "false"))
        .andExpect(status().isOk());
    verify(reports)
        .list(
            argThat(
                request ->
                    request.getType().equals("WEEKLY")
                        && request.getStart().equals(LocalDate.of(2026, 10, 1))
                        && !request.getLatestOnly()));
  }

  @Test
  void invalidPageSizeIsRejectedBeforeCallingService() throws Exception {
    mvc.perform(get("/api/v1/reports").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    verifyNoInteractions(reports);
  }

  @Test
  void invalidNumberOrReportTypeUsesTheSame400Envelope() throws Exception {
    mvc.perform(get("/api/v1/reports").param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    mvc.perform(get("/api/v1/reports").param("type", "OTHER"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    verifyNoInteractions(reports);
  }

  @Test
  void optionalSignalDatesMayRemainUnspecified() throws Exception {
    mvc.perform(get("/api/v1/instruments/300476.SZ/signals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
    verify(signals)
        .list(
            eq("300476.SZ"),
            argThat(request -> request.getStart() == null && request.getEnd() == null));
  }

  @Test
  void reversedOptionalDatesAreRejected() throws Exception {
    mvc.perform(
            get("/api/v1/instruments/300476.SZ/signals")
                .param("start", "2026-10-31")
                .param("end", "2026-10-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    verifyNoInteractions(signals);
  }

  @Test
  void statisticsRequiresTheQuerySymbol() throws Exception {
    mvc.perform(
            get("/api/v1/backtest/signals").param("start", "2026-10-01").param("end", "2026-10-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    verifyNoInteractions(statistics);
  }

  @Test
  void jobPageKeepsDefaultValues() throws Exception {
    mvc.perform(get("/api/v1/admin/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(20));
    verify(jobs).page(argThat(request -> request.getPage() == 1 && request.getSize() == 20));
  }
}
