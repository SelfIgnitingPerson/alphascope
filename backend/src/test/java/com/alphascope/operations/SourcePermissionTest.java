package com.alphascope.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.exception.*;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.indicator.controller.IndicatorController;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.entity.SourceLicense;
import com.alphascope.operations.mapper.*;
import com.alphascope.operations.policy.*;
import com.alphascope.operations.service.impl.SourceLicenseServiceImpl;
import com.alphascope.signal.controller.SignalController;
import com.alphascope.signal.entity.SignalEvent;
import com.alphascope.signal.service.SignalService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 真实PUBLIC许可策略与HTTP响应契约：来源失效时指标、规则和原始数据同样受控。
 *
 * @author chenxuefeng
 */
class SourcePermissionTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T08:00:00Z"), ZoneOffset.UTC);
  private final SourceLicenseMapper mapper = mock(SourceLicenseMapper.class);
  private final SourceEvidenceMapper evidence = mock(SourceEvidenceMapper.class);

  private SourceLicenseServiceImpl policy(List<SourceLicense> grants) {
    when(mapper.latest()).thenReturn(grants);
    var access = new AccessProperties();
    access.setMode(AccessProperties.Mode.PUBLIC);
    var value =
        new SourceLicenseServiceImpl(
            mapper,
            new ProductProperties(),
            access,
            new AlphaScopeProperties(),
            new MockEnvironment(),
            clock);
    value.setEvidenceMapper(evidence);
    return value;
  }

  private SourceLicense grant(String source, boolean valid) {
    var row = new SourceLicense();
    row.setSource(source);
    row.setValidFrom(LocalDate.of(2026, 1, 1));
    row.setValidUntil(valid ? LocalDate.of(2026, 12, 31) : LocalDate.of(2026, 11, 1));
    row.setPermissionsJson("[\"DISPLAY\",\"DERIVED_REPORT\",\"EXPORT\"]");
    return row;
  }

  @Test
  void missingExpiredAndUnrelatedLicensesAreCheckedAgainstActualIndicatorBatch() throws Exception {
    var service = mock(IndicatorService.class);
    var row = new IndicatorSnapshot();
    row.setMa20(BigDecimal.valueOf(100));
    row.setSourceBatch("fixture-batch");
    when(service.list(anyString(), any())).thenReturn(List.of(row));
    when(evidence.batchSources(Set.of("fixture-batch"))).thenReturn(List.of("BAOSTOCK"));
    for (var grants :
        List.of(
            List.<SourceLicense>of(),
            List.of(grant("BAOSTOCK", false)),
            List.of(grant("BAOSTOCK", true), grant("SINA", false)))) {
      var mvc =
          MockMvcBuilders.standaloneSetup(new IndicatorController(service))
              .setControllerAdvice(
                  new ApiExceptionHandler(), new ResearchResponseAdvice(policy(grants)))
              .build();
      var request =
          mvc.perform(
              get("/api/v1/instruments/002837.SZ/indicators?start=2026-11-17&end=2026-11-17"));
      if (grants.stream()
          .anyMatch(
              x ->
                  x.getSource().equals("BAOSTOCK")
                      && x.getValidUntil().isAfter(LocalDate.of(2026, 11, 17))))
        request.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].ma20").value(100));
      else
        request.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").isEmpty());
    }
  }

  @Test
  void signalsRequireProvenanceAndDoNotExposeLegacyUntraceableValues() throws Exception {
    var service = mock(SignalService.class);
    var row = new SignalEvent();
    row.setStatus("NOT_TRIGGERED");
    row.setEvidenceJson("{\"values\":{\"cashflow\":100}}");
    when(service.list(anyString(), any())).thenReturn(List.of(row));
    var mvc =
        MockMvcBuilders.standaloneSetup(new SignalController(service))
            .setControllerAdvice(
                new ApiExceptionHandler(),
                new ResearchResponseAdvice(policy(List.of(grant("BAOSTOCK", true)))))
            .build();
    mvc.perform(get("/api/v1/instruments/002837.SZ/signals"))
        .andExpect(status().isServiceUnavailable());
    row.setEvidenceJson("{\"inputSources\":[\"BAOSTOCK\"],\"values\":{\"cashflow\":100}}");
    mvc.perform(get("/api/v1/instruments/002837.SZ/signals")).andExpect(status().isOk());
  }

  @Test
  void aliasesAndExportAreIndependentOfConfiguredUnusedSources() {
    var financial = grant("EASTMONEY_FINANCIAL", true);
    financial.setPermissionsJson("[\"DISPLAY\"]");
    var service = policy(List.of(financial, grant("SINA", false)));
    assertDoesNotThrow(
        () ->
            service.requireEvidence(
                Map.of("source", "EASTMONEY_FREE", "netProfit", 100), "DISPLAY"));
    assertThrows(
        BusinessException.class,
        () ->
            service.requireEvidence(
                Map.of("source", "EASTMONEY_FREE", "netProfit", 100), "EXPORT"));
    assertThrows(
        BusinessException.class,
        () -> service.requireEvidence(Map.of("source", "SINA", "close", 100), "DISPLAY"));
  }

  @Test
  void evidenceDownloadRequiresExportAndActualFrozenSources() throws Exception {
    var tasks = mock(com.alphascope.backtest.service.ValidationTaskService.class);
    when(tasks.evidence(5)).thenReturn("{\"inputSources\":[\"BAOSTOCK\"],\"close\":100}");
    var display = grant("BAOSTOCK", true);
    display.setPermissionsJson("[\"DISPLAY\"]");
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new com.alphascope.backtest.controller.ValidationTaskController(tasks))
            .setControllerAdvice(
                new ApiExceptionHandler(), new ResearchResponseAdvice(policy(List.of(display))))
            .build();
    mvc.perform(get("/api/v1/backtest/validation-tasks/5/evidence"))
        .andExpect(status().isServiceUnavailable());
    var complete = grant("BAOSTOCK", true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new com.alphascope.backtest.controller.ValidationTaskController(tasks))
            .setControllerAdvice(
                new ApiExceptionHandler(),
                new ResearchResponseAdvice(policy(List.of(complete, grant("SINA", false)))))
            .build();
    mvc.perform(get("/api/v1/backtest/validation-tasks/5/evidence"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"alphascope-validation-5.json\""));
    when(tasks.evidence(5)).thenReturn("{\"inputSources\":[\"SINA\"],\"close\":100}");
    mvc.perform(get("/api/v1/backtest/validation-tasks/5/evidence"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void personalValuationStillRequiresItsActualFinancialSourceLicense() throws Exception {
    var service = mock(com.alphascope.fundamental.service.InvestmentEvidenceService.class);
    var thesis =
        new com.alphascope.fundamental.dto.ValuationThesisInput(
            "600000.SH",
            LocalDate.of(2026, 11, 17),
            LocalDate.of(2026, 12, 17),
            BigDecimal.ONE,
            BigDecimal.valueOf(2),
            BigDecimal.TEN,
            BigDecimal.valueOf(20),
            BigDecimal.valueOf(.2),
            true,
            "公开依据核对EPS和股本，以及估值倍数",
            "盈利质量恶化后撤销本情景",
            "https://example.test/report",
            "EASTMONEY_FINANCIAL");
    when(service.thesis(any())).thenReturn(thesis);
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new com.alphascope.fundamental.controller.InvestmentEvidenceController(service))
            .setControllerAdvice(
                new ApiExceptionHandler(),
                new ResearchResponseAdvice(policy(List.of(grant("SINA", true)))))
            .build();
    mvc.perform(get("/api/v1/stocks/600000.SH/valuation-thesis").param("asOf", "2026-11-17"))
        .andExpect(status().isServiceUnavailable());
    mvc =
        MockMvcBuilders.standaloneSetup(
                new com.alphascope.fundamental.controller.InvestmentEvidenceController(service))
            .setControllerAdvice(
                new ApiExceptionHandler(),
                new ResearchResponseAdvice(policy(List.of(grant("EASTMONEY_FINANCIAL", true)))))
            .build();
    mvc.perform(get("/api/v1/stocks/600000.SH/valuation-thesis").param("asOf", "2026-11-17"))
        .andExpect(status().isOk());
  }

  @Test
  void allControllersDeclareDataBoundary() throws Exception {
    var scanner =
        new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(
            false);
    scanner.addIncludeFilter(
        new org.springframework.core.type.filter.AnnotationTypeFilter(
            org.springframework.web.bind.annotation.RestController.class));
    for (var bean : scanner.findCandidateComponents("com.alphascope")) {
      var type = Class.forName(bean.getBeanClassName());
      assertTrue(
          type.isAnnotationPresent(ResearchData.class)
              || type.isAnnotationPresent(OperationalData.class),
          type.getName() + "未声明数据边界");
    }
  }

  @Test
  void businessRatiosKeepActualFinancialPermissionAndNoDataRemainsReadable() throws Exception {
    var service = mock(com.alphascope.intelligence.service.BusinessEvidenceService.class);
    var date = LocalDate.of(2026, 11, 17);
    var result =
        new com.alphascope.intelligence.vo.BusinessEvidenceView(
            date,
            date,
            "EASTMONEY_FINANCIAL",
            "https://example.test/financial",
            .5,
            null,
            null,
            List.of("现金兑现需核对"),
            List.of("缺同行"),
            "UNVERIFIED",
            Set.of("EASTMONEY_FINANCIAL"),
            List.of());
    when(service.read(any())).thenReturn(result);
    for (boolean correct : List.of(false, true)) {
      var mvc =
          MockMvcBuilders.standaloneSetup(
                  new com.alphascope.intelligence.controller.BusinessEvidenceController(service))
              .setControllerAdvice(
                  new ApiExceptionHandler(),
                  new ResearchResponseAdvice(
                      policy(List.of(grant(correct ? "EASTMONEY_FINANCIAL" : "SINA", true)))))
              .build();
      mvc.perform(get("/api/v1/instruments/002837.SZ/business-evidence?asOf=2026-11-17"))
          .andExpect(correct ? status().isOk() : status().isServiceUnavailable());
    }
    when(service.read(any()))
        .thenReturn(
            new com.alphascope.intelligence.vo.BusinessEvidenceView(
                date,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of("缺少财报"),
                "UNVERIFIED",
                Set.of(),
                List.of()));
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new com.alphascope.intelligence.controller.BusinessEvidenceController(service))
            .setControllerAdvice(
                new ApiExceptionHandler(), new ResearchResponseAdvice(policy(List.of())))
            .build();
    mvc.perform(get("/api/v1/instruments/002837.SZ/business-evidence?asOf=2026-11-17"))
        .andExpect(status().isOk());
  }
}
