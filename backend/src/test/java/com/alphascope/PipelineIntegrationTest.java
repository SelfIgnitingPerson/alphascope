package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.fundamental.dto.FinancialInput;
import com.alphascope.fundamental.entity.EarningsForecastSnapshot;
import com.alphascope.fundamental.mapper.FinancialSyncMapper;
import com.alphascope.fundamental.mapper.ForecastMapper;
import com.alphascope.fundamental.provider.EastmoneyFinancialProvider;
import com.alphascope.fundamental.service.FinancialWriteService;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.industry.dto.MetricInput;
import com.alphascope.industry.dto.SourceInput;
import com.alphascope.industry.mapper.IndustryMapper;
import com.alphascope.industry.mapper.ThemeCohortMapper;
import com.alphascope.industry.service.EvidenceService;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.service.impl.ThemeCohortServiceImpl;
import com.alphascope.intelligence.dto.ReportSearchRequest;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.MarketBarQuery;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchSnapshotQuery;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.entity.MarketFactorSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.mapper.MarketObservationMapper;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketWriteService;
import com.alphascope.scheduler.dto.DataQualityStatus;
import com.alphascope.scheduler.dto.JobCompletion;
import com.alphascope.scheduler.dto.JobStatus;
import com.alphascope.scheduler.dto.JobStopRequest;
import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.entity.JobStage;
import com.alphascope.scheduler.mapper.JobMapper;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.signal.mapper.SignalMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 真实MySQL迁移与MyBatis集成测试；显式选择测试数据库后再启用，防止误写生产库。
 *
 * @author chenxuefeng
 */
@SpringBootTest(properties = {"alphascope.provider=demo", "alphascope.jobs.worker-enabled=false"})
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PipelineIntegrationTest {
  /** 使用一次性数据库，绝不把模拟测试数据写入用户当前的真实行情库。 */
  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.0.40").withCommand("--max-allowed-packet=64M");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
    properties.add("spring.datasource.username", MYSQL::getUsername);
    properties.add("spring.datasource.password", MYSQL::getPassword);
    properties.add("spring.flyway.user", () -> "root");
    properties.add("spring.flyway.password", MYSQL::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired JobService jobs;
  @Autowired MarketService market;
  @Autowired ReportService reports;
  @Autowired JdbcTemplate jdbc;
  @Autowired MarketWriteService writer;
  @Autowired MarketMapper marketMapper;
  @Autowired FinancialSyncMapper financialSync;
  @Autowired FundamentalService fundamentals;
  @Autowired TransactionTemplate tx;

  @Autowired ThemeCohortMapper cohorts;
  @Autowired IndustryMapper industry;
  @Autowired IndustryService themeService;
  @Autowired ObjectMapper json;
  @Autowired ForecastMapper forecastMapper;
  @Autowired MarketObservationMapper observationMapper;
  @Autowired MarketBreadthMapper breadthMapper;

  @Test
  void importedFullMarketRemainsVerifiableAfterMysqlAndRebuildsWithoutProviderRequests() {
    var day = LocalDate.of(2026, 10, 28);
    var members =
        java.util.stream.IntStream.range(0, 4000)
            .mapToObj(
                index -> {
                  String symbol =
                      index < 2000
                          ? (600000 + index) + ".SH"
                          : index < 3900
                              ? String.format(java.util.Locale.ROOT, "%06d.SZ", index - 2000)
                              : (920000 + index - 3900) + ".BJ";
                  return new com.alphascope.market.dto.HistoricalFullMarketArchive.Member(
                      symbol, false, null);
                })
            .toList();
    var prices =
        members.stream()
            .map(
                member ->
                    new com.alphascope.market.dto.HistoricalFullMarketArchive.Quote(
                        member.symbol(),
                        BigDecimal.TEN,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(2)))
            .toList();
    var archive =
        new com.alphascope.market.dto.HistoricalFullMarketArchive(
            day,
            "BIGQUANT",
            day.plusDays(1).atTime(9, 0).atOffset(java.time.ZoneOffset.ofHours(8)),
            Map.of("SSE", 2000, "SZSE", 1900, "BSE", 100),
            Map.of(
                "SSE",
                "https://www.sse.com.cn/fixture",
                "SZSE",
                "https://www.szse.cn/fixture",
                "BSE",
                "https://www.bse.cn/fixture"),
            members,
            prices);
    var snapshot =
        com.alphascope.market.calculator.HistoricalFullMarketAssembler.assemble(
            archive, day.plusDays(1).atTime(10, 0));
    var query = ResearchSnapshotQuery.builder().date(day).source("BIGQUANT").loadRaw(true).build();
    var legacy =
        com.alphascope.common.util.Jsons.read(
            snapshot.getRawJson(), com.fasterxml.jackson.databind.node.ObjectNode.class);
    ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.path("rosterEvidence"))
        .remove("rosterSources");
    String legacyRaw = legacy.toString();
    var old =
        com.alphascope.market.entity.MarketBreadthSnapshot.builder()
            .tradeDate(day)
            .universeScope("FULL_A")
            .source("BIGQUANT")
            .contentHash(com.alphascope.common.util.Jsons.hash(legacyRaw))
            .metricsJson(snapshot.getMetricsJson())
            .rawJson(legacyRaw)
            .fetchedAt(snapshot.getFetchedAt())
            .build();
    breadthMapper.save(old);
    assertNull(breadthMapper.fullForDate(query));
    assertFalse(
        com.alphascope.market.calculator.UniverseCoverageCalculator.calculate(old)
            .coverage()
            .rosterVerified());
    breadthMapper.save(snapshot);
    var saved = breadthMapper.fullForDate(query);
    breadthMapper.save(snapshot);
    assertEquals(saved.getId(), breadthMapper.fullForDate(query).getId());
    assertTrue(
        com.alphascope.market.calculator.UniverseCoverageCalculator.calculate(saved)
            .coverage()
            .rosterVerified());
    var real = org.mockito.Mockito.mock(MarketService.class);
    org.mockito.Mockito.when(real.expectedDates(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Set.of(day));
    var router =
        org.mockito.Mockito.mock(com.alphascope.market.provider.research.ResearchDataRouter.class);
    var full =
        new com.alphascope.market.service.impl.FullMarketServiceImpl(
            null,
            real,
            router,
            breadthMapper,
            observationMapper,
            java.time.Clock.systemUTC(),
            json);
    full.rebuildImported(day);
    var observation = full.observation(day);
    assertEquals("FULL_A", observation.scope());
    assertEquals(4000, observation.breadth().trading());
    assertTrue(observation.inputSources().contains("BIGQUANT"));
    assertNull(observation.breadth().ratio60());
    org.mockito.Mockito.verifyNoInteractions(router);
  }

  @Autowired FinancialWriteService manualFinancials;
  @Autowired EvidenceService evidenceService;

  @Autowired JobMapper jobMapper;
  @Autowired SignalMapper signalMapper;

  /** 验证停止意图、进程归属和阶段质量在真实数据库中独立保存。 */
  @Autowired com.alphascope.market.mapper.MarketMapper latestSourceMapper;

  @Test
  void latestSourceLookupStartsFromInstrumentAndRespectsDateModeAndTieBreak() {
    jdbc.update(
        "INSERT INTO instrument(symbol,name,exchange,type) VALUES('609999.SH','最新来源隔离测试','SH','STOCK')");
    Long instrument =
        jdbc.queryForObject("SELECT id FROM instrument WHERE symbol='609999.SH'", Long.class);
    for (int mode = 0; mode <= 1; mode++) {
      for (int day = 1; day <= 3; day++)
        jdbc.update(
            "INSERT INTO daily_bar(instrument_id,trade_date,open,high,low,close,volume,amount,source,fetched_at,batch_id,is_synthetic) VALUES(?, ?,10,11,9,10,100,1000,'FIRST','2026-10-30 10:00:00','first',?)",
            instrument,
            java.time.LocalDate.of(2026, 10, day),
            mode);
    }
    jdbc.update(
        "INSERT INTO daily_bar(instrument_id,trade_date,open,high,low,close,volume,amount,source,fetched_at,batch_id,is_synthetic) VALUES(?,'2026-10-03',10,11,9,10,100,1000,'SECOND','2026-10-30 10:00:00','second',0)",
        instrument);
    var real =
        latestSourceMapper.latestSources(false).stream()
            .filter(row -> row.getSymbol().equals("609999.SH"))
            .findFirst()
            .orElseThrow();
    var demo =
        latestSourceMapper.latestSources(true).stream()
            .filter(row -> row.getSymbol().equals("609999.SH"))
            .findFirst()
            .orElseThrow();
    assertEquals(java.time.LocalDate.of(2026, 10, 3), real.getTradeDate());
    assertEquals("SECOND", real.getSource());
    assertEquals("FIRST", demo.getSource());
  }

  @Test
  void durableCancellationCannotBeOverwrittenByCompletion() {
    var row = new JobExecution();
    row.setJobName("DAILY");
    row.setBusinessDate(LocalDate.of(1901, 1, 2));
    row.setAttempt(1);
    row.setDataScope("TEST_CONTROL");
    jobMapper.start(row);
    assertEquals(1, jobMapper.claim(row.getId(), "owner"));
    jobMapper.stage(row.getId(), "RESEARCH_DATA");
    var stage =
        JobStage.builder()
            .jobId(row.getId())
            .stageName("RESEARCH_DATA")
            .status("SUCCESS")
            .dataStatus("PARTIAL")
            .detailsJson("{\"quality\":{\"status\":\"PARTIAL\",\"issues\":[]}}")
            .build();
    jobMapper.stageEnd(stage);
    assertEquals("SUCCESS", jobMapper.stages(row.getId()).getFirst().getStatus());
    assertEquals("PARTIAL", jobMapper.stages(row.getId()).getFirst().getDataStatus());
    assertTrue(jobMapper.stages(row.getId()).getFirst().getDetailsJson().contains("PARTIAL"));
    var completion =
        JobCompletion.builder()
            .jobId(row.getId())
            .ownerId("other")
            .status(JobStatus.SUCCESS)
            .dataStatus(DataQualityStatus.PARTIAL)
            .build();
    assertEquals(0, jobMapper.complete(completion));
    assertEquals(
        1,
        jobMapper.requestStop(
            JobStopRequest.builder()
                .jobId(row.getId())
                .status(JobStatus.CANCEL_REQUESTED)
                .reason("用户取消")
                .build()));
    assertEquals(
        0,
        jobMapper.complete(
            JobCompletion.builder()
                .jobId(row.getId())
                .ownerId("owner")
                .status(JobStatus.SUCCESS)
                .dataStatus(DataQualityStatus.AVAILABLE)
                .build()));
    assertEquals(
        0,
        jobMapper.requestStop(
            JobStopRequest.builder()
                .jobId(row.getId())
                .ownerId("owner")
                .status(JobStatus.TIMEOUT_REQUESTED)
                .reason("后到超时")
                .build()));
    assertEquals(
        1,
        jobMapper.complete(
            JobCompletion.builder()
                .jobId(row.getId())
                .ownerId("owner")
                .status(JobStatus.CANCELLED)
                .dataStatus(DataQualityStatus.PARTIAL)
                .build()));
    assertEquals("CANCELLED", jobMapper.get(row.getId()).getStatus());
    assertEquals("PARTIAL", jobMapper.get(row.getId()).getDataStatus());
    row.setId(null);
    row.setDataScope("TEST_PENDING");
    row.setAttempt(2);
    jobMapper.start(row);
    assertEquals(
        1,
        jobMapper.requestStop(
            JobStopRequest.builder()
                .jobId(row.getId())
                .status(JobStatus.CANCEL_REQUESTED)
                .reason("取消排队")
                .build()));
    assertEquals("CANCELLED", jobMapper.get(row.getId()).getStatus());
    assertNotNull(jobMapper.get(row.getId()).getEndedAt());
    assertEquals(0, jobMapper.claim(row.getId(), "owner"));
  }

  /** 页面仍限制一千条，验证查询则必须完整取回所选区间，避免被静默截断。 */
  @Test
  void validationHistoryDoesNotSilentlyTruncateAtOneThousandRows() {
    long instrument =
        jdbc.queryForObject("SELECT id FROM instrument WHERE symbol='300476.SZ'", Long.class);
    try {
      for (int i = 0; i < 1001; i++) {
        jdbc.update(
            "INSERT INTO signal_event(instrument_id,trade_date,rule_code,rule_version,severity,evidence_json,status,is_synthetic) VALUES(?,?,'TEST_FULL_HISTORY','TEST','LOW','{}','TRIGGERED',1)",
            instrument,
            LocalDate.of(1990, 1, 1).plusDays(i));
      }
      var query =
          InstrumentHistoryQuery.builder()
              .symbol("300476.SZ")
              .startDate(LocalDate.of(1990, 1, 1))
              .endDate(LocalDate.of(1993, 1, 1))
              .synthetic(true)
              .build();
      assertEquals(1000, signalMapper.list(query).size());
      assertEquals(1001, signalMapper.evaluationRecords(query).size());
    } finally {
      jdbc.update("DELETE FROM signal_event WHERE rule_code='TEST_FULL_HISTORY'");
    }
  }

  /** 验证请求对象在 OpenAPI 中仍展开为原有查询参数，必填日期和 URL 标识保持分离。 */
  @Test
  void requestObjectsKeepThePublishedQueryParameterContract() throws Exception {
    var raw =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var api = json.readTree(raw);
    var dates =
        api.path("paths")
            .path("/api/v1/instruments/{symbol}/indicators")
            .path("get")
            .path("parameters");
    var names = new HashMap<String, JsonNode>();
    dates.forEach(parameter -> names.put(parameter.path("name").asText(), parameter));
    assertEquals(Set.of("symbol", "start", "end"), names.keySet());
    assertEquals("path", names.get("symbol").path("in").asText());
    assertEquals("query", names.get("start").path("in").asText());
    assertTrue(names.get("start").path("required").asBoolean());
    assertTrue(names.get("end").path("required").asBoolean());
    var reportNames = new HashSet<String>();
    api.path("paths")
        .path("/api/v1/reports")
        .path("get")
        .path("parameters")
        .forEach(parameter -> reportNames.add(parameter.path("name").asText()));
    assertEquals(Set.of("type", "start", "end", "page", "size", "latestOnly"), reportNames);
  }

  /** 验证通过 Service 接口注入的事务代理在后续行失败时回滚整批行情和不可变修订。 */
  @Test
  void interfaceInjectedWriteServiceRollsBackWholeBatchWhenLaterBarFails() {
    var first = sample("TX_ROLLBACK", false, "1");
    var invalid =
        new DailyBarDto(
            first.symbol(),
            first.tradeDate().plusDays(1),
            first.open(),
            first.high(),
            first.low(),
            first.close(),
            first.volume(),
            new BigDecimal("1E100"),
            first.adjustmentFactor(),
            first.provider(),
            first.fetchedAt(),
            false);
    long instrumentId = market.instrument(first.symbol()).getId();
    assertThrows(
        DataIntegrityViolationException.class,
        () -> writer.persist(instrumentId, new MarketWindow(List.of(first, invalid), Map.of())));
    for (String table : List.of("daily_bar", "adjustment_factor", "market_data_revision")) {
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE source=?", Integer.class, "TX_ROLLBACK"),
          table + " must roll back with the failed batch");
    }
  }

  @Test
  void xmlWritesKeepGeneratedIdsNullValuesAndImmutableFinancialVersions() {
    var period = LocalDate.of(2002, 6, 30);
    var published = LocalDateTime.of(2002, 8, 28, 9, 0);
    var input =
        new FinancialInput(
            "300476.SZ",
            period,
            "H1_CUMULATIVE",
            published,
            1,
            new BigDecimal("1000000.25"),
            new BigDecimal("-123.45"),
            null,
            null,
            new BigDecimal("0.20"),
            null,
            "https://example.test/public-report/2002-h1");
    manualFinancials.insert(input);
    var saved =
        fundamentals.reports("300476.SZ", published.toLocalDate()).stream()
            .filter(report -> period.equals(report.getPeriodEnd()))
            .findFirst()
            .orElseThrow();
    assertEquals(published, saved.getPublishedAt());
    assertEquals(0, new BigDecimal("-123.45").compareTo(saved.getNetProfit()));
    assertNull(saved.getOperatingCashflow());
    assertNull(saved.getGrossMargin());
    assertThrows(DuplicateKeyException.class, () -> manualFinancials.insert(input));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_report WHERE instrument_id=? AND period_end=? AND report_type=?",
            Integer.class,
            saved.getInstrumentId(),
            period,
            "H1_CUMULATIVE"));
    assertTrue(
        fundamentals.reports("300476.SZ", published.toLocalDate().minusDays(1)).stream()
            .noneMatch(report -> period.equals(report.getPeriodEnd())));

    long sourceId =
        evidenceService.source(
            new SourceInput(
                "PUBLIC_REPORT",
                "https://example.test/industry/2002",
                "公开产业统计",
                published,
                "可核验的公开材料"));
    assertTrue(sourceId > 0);
    evidenceService.metric(
        new MetricInput(
            "PCB", "XML_CONTRACT", period, published, new BigDecimal("12.50"), "CNY", sourceId));
    var metric =
        evidenceService.metrics("PCB", published.toLocalDate()).stream()
            .filter(value -> "XML_CONTRACT".equals(value.getMetricCode()))
            .findFirst()
            .orElseThrow();
    assertEquals(sourceId, metric.getSourceDocumentId());
    assertEquals("公开产业统计", metric.getTitle());
    assertEquals(0, new BigDecimal("12.50").compareTo(metric.getValue()));
  }

  @Test
  void latestMarketSnapshotReadsLargeJsonByPrimaryKeyWithoutFilesort() throws Exception {
    var date = LocalDate.of(2000, 1, 3);
    breadthMapper.save(
        MarketBreadthSnapshot.builder()
            .tradeDate(date)
            .contentHash("3".repeat(64))
            .metricsJson("{\"status\":\"COMPLETE\"}")
            .rawJson(json.writeValueAsString(Map.of("padding", "x".repeat(1500000))))
            .fetchedAt(date.atTime(16, 0))
            .build());
    var metadata =
        breadthMapper.forSource(
            ResearchSnapshotQuery.builder().date(date).source("BAOSTOCK").loadRaw(false).build());
    var evidence =
        breadthMapper.forSource(
            ResearchSnapshotQuery.builder().date(date).source("BAOSTOCK").loadRaw(true).build());
    assertNotNull(metadata);
    assertNull(metadata.getRawJson(), "元数据查询不加载大JSON");
    assertEquals(metadata.getId(), evidence.getId());
    assertTrue(evidence.getRawJson().length() > 1500000);
    assertNull(
        breadthMapper.forSource(
            ResearchSnapshotQuery.builder().date(date).source("TUSHARE").loadRaw(true).build()),
        "不跨来源借用快照");
    String plan =
        jdbc.queryForObject(
            "EXPLAIN FORMAT=JSON SELECT * FROM market_breadth_snapshot WHERE id=(SELECT MAX(id) FROM market_breadth_snapshot WHERE trade_date='2000-01-03' AND universe_scope='SH_SZ_A' AND source='BAOSTOCK')",
            String.class);
    assertTrue(
        json.readTree(plan).findValues("using_filesort").stream().noneMatch(JsonNode::asBoolean));
  }

  @Test
  void forecastFirstObservationAndImmutableFactorEvidenceArePreserved() throws Exception {
    var observed = LocalDateTime.of(2026, 11, 1, 9, 0);
    long instrument = market.instrument("300476.SZ").getId();
    forecastMapper.save(
        EarningsForecastSnapshot.builder()
            .instrumentId(instrument)
            .observationDate(observed.toLocalDate())
            .availableAt(observed)
            .sourceUrl("https://data.eastmoney.com/report/profitforecast.jshtml")
            .contentHash("1".repeat(64))
            .metricsJson("{\"ratingAgencies\":7,\"estimates\":[{\"year\":2026,\"eps\":8.17}]}")
            .rawJson("{}")
            .build());
    forecastMapper.save(
        EarningsForecastSnapshot.builder()
            .instrumentId(instrument)
            .observationDate(observed.toLocalDate())
            .availableAt(observed.plusHours(1))
            .sourceUrl("https://data.eastmoney.com/report/profitforecast.jshtml")
            .contentHash("1".repeat(64))
            .metricsJson("{\"ratingAgencies\":7,\"estimates\":[{\"year\":2026,\"eps\":8.17}]}")
            .rawJson("{}")
            .build());
    assertTrue(
        forecastMapper.visible("300476.SZ", LocalDate.of(2026, 10, 30).atTime(16, 10)).isEmpty());
    var rows = forecastMapper.visible("300476.SZ", observed.plusHours(2));
    assertEquals(1, rows.size());
    assertEquals(
        "https://data.eastmoney.com/report/profitforecast.jshtml", rows.getFirst().getSourceUrl());
    assertEquals(observed, rows.getFirst().getFetchedAt());
    assertEquals("{}", rows.getFirst().getRawJson());
    assertEquals(
        8.17,
        json.readTree(rows.getFirst().getMetricsJson())
            .path("estimates")
            .get(0)
            .path("eps")
            .asDouble());
    Object at = rows.getFirst().getAvailableAt();
    assertEquals(observed, at instanceof java.sql.Timestamp t ? t.toLocalDateTime() : at);
    observationMapper.factorSave(
        MarketFactorSnapshot.builder()
            .symbol("sh.600000")
            .verifiedThrough(LocalDate.of(2026, 10, 30))
            .contentHash("2".repeat(64))
            .rawJson("[]")
            .fetchedAt(observed)
            .build());
    var first =
        observationMapper.factor(
            ResearchFactorRequest.builder()
                .symbol("sh.600000")
                .source("BAOSTOCK")
                .start(LocalDate.of(1990, 1, 1))
                .end(LocalDate.of(2026, 10, 30))
                .build());
    observationMapper.factorSave(
        MarketFactorSnapshot.builder()
            .symbol("sh.600000")
            .verifiedThrough(LocalDate.of(2026, 11, 2))
            .contentHash("2".repeat(64))
            .rawJson("[]")
            .fetchedAt(observed.plusDays(1))
            .build());
    var second =
        observationMapper.factor(
            ResearchFactorRequest.builder()
                .symbol("sh.600000")
                .source("BAOSTOCK")
                .start(LocalDate.of(1990, 1, 1))
                .end(LocalDate.of(2026, 11, 2))
                .build());
    assertEquals(first.getId(), second.getId());
    assertEquals(first.getRawJson(), second.getRawJson());
    assertEquals(first.getFetchedAt(), second.getFetchedAt());
  }

  @Test
  void disclosedCohortsAreIdempotentAsOfVisibleAndSeparateFromWatchlistAndDemo() throws Exception {
    int watches = jdbc.queryForObject("SELECT COUNT(*) FROM watchlist", Integer.class);
    var realMode = org.mockito.Mockito.mock(MarketService.class);
    var importer = new ThemeCohortServiceImpl(cohorts, industry, realMode, tx, json);
    var date = LocalDate.of(2026, 10, 30);
    importer.prepare(date);
    var before = jdbc.queryForList("SELECT * FROM theme_research_cohort ORDER BY id");
    importer.prepare(date);
    assertEquals(before, jdbc.queryForList("SELECT * FROM theme_research_cohort ORDER BY id"));
    assertEquals(5, before.size());
    assertEquals(
        22, jdbc.queryForObject("SELECT COUNT(*) FROM theme_research_member", Integer.class));
    assertEquals(watches, jdbc.queryForObject("SELECT COUNT(*) FROM watchlist", Integer.class));
    assertNull(industry.cohort("PCB", LocalDate.of(2026, 3, 21)));
    assertEquals(6, industry.members("PCB", LocalDate.of(2026, 3, 22)).size());
    assertNull(industry.cohort("COOLING", LocalDate.of(2026, 5, 22)));
    assertEquals(3, industry.members("COOLING", LocalDate.of(2026, 5, 23)).size());
    assertNull(industry.cohort("CPO", LocalDate.of(2024, 8, 24)));
    assertEquals(4, industry.members("CPO", LocalDate.of(2024, 8, 25)).size());
    assertNull(industry.cohort("SEMI_EQUIPMENT", LocalDate.of(2026, 1, 27)));
    assertEquals(6, industry.members("SEMI_EQUIPMENT", LocalDate.of(2026, 1, 28)).size());
    assertNull(industry.cohort("MEMORY", LocalDate.of(2026, 7, 14)));
    assertEquals(3, industry.members("MEMORY", LocalDate.of(2026, 7, 15)).size());
    assertEquals(LocalDate.of(2026, 3, 22), industry.membershipSince("PCB", "300476.SZ", date));
    assertEquals(
        LocalDate.of(2026, 10, 30),
        jdbc.queryForObject(
            "SELECT it.effective_from FROM instrument_theme it JOIN instrument i ON i.id=it.instrument_id JOIN theme t ON t.id=it.theme_id WHERE i.symbol='300476.SZ' AND t.code='PCB'",
            LocalDate.class));
    assertEquals(
        5,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM theme_research_cohort c JOIN source_document d ON d.id=c.source_document_id WHERE d.source_type='EXCHANGE_DISCLOSURE' AND d.published_at<c.available_at AND JSON_UNQUOTE(JSON_EXTRACT(c.definition_json,'$.pdfSha256')) REGEXP '^[a-f0-9]{64}$'",
            Integer.class));
    assertEquals(1, industry.legacyMembers("PCB", date).size());
    assertTrue(
        themeService.snapshot(date).isEmpty(), "Demo must not consume real research cohorts");
  }

  @Test
  void automaticFinancialVersionsRemainImmutableAndFutureRevisionIsHidden() throws Exception {
    var json = new ObjectMapper();
    var income =
        json.readTree(
            """
      [{"SECUCODE":"002837.SZ","REPORT_DATE":"2026-06-30 00:00:00","REPORT_TYPE":"中报",
        "NOTICE_DATE":"2026-08-28 00:00:00","UPDATE_DATE":"2026-08-28 00:00:00",
        "OPERATE_INCOME":100000000,"OPERATE_COST":70000000,"PARENT_NETPROFIT":10000000,
        "OPERATE_INCOME_YOY":25.5,"PARENT_NETPROFIT_YOY":4.25}]
      """);
    var fetched = LocalDateTime.of(2026, 10, 31, 19, 0);
    var a =
        EastmoneyFinancialProvider.parse(
                "002837.SZ", income, json.createArrayNode(), fetched, "https://example.test")
            .getFirst();
    ((ObjectNode) income.get(0))
        .put("UPDATE_DATE", "2026-10-30 00:00:00")
        .put("PARENT_NETPROFIT", 20000000);
    var b =
        EastmoneyFinancialProvider.parse(
                "002837.SZ", income, json.createArrayNode(), fetched, "https://example.test")
            .getFirst();
    long id = market.instrument("002837.SZ").getId();
    tx.executeWithoutResult(
        status -> {
          financialSync.lockInstrument(id);
          financialSync.insert(id, a, fetched);
          assertEquals(1, financialSync.exists(id, a.hash()));
          financialSync.insert(id, b, fetched);
        });
    var visible = fundamentals.reports("002837.SZ", LocalDate.of(2026, 10, 30));
    assertEquals(1, visible.size());
    assertEquals(10000000, ((Number) visible.getFirst().getNetProfit()).doubleValue());
    var revisions =
        jdbc.queryForList(
            "SELECT version,raw_json,content_hash,publication_precision FROM financial_report WHERE instrument_id=? ORDER BY version",
            id);
    assertEquals(2, revisions.size());
    assertEquals("DAY", revisions.getFirst().get("publication_precision"));
    assertNotEquals(
        revisions.getFirst().get("content_hash"), revisions.getLast().get("content_hash"));
  }

  @Test
  void replayFiveDaysIsIdempotentAndSyntheticSafe() {
    for (int day = 26; day <= 30; day++) {
      LocalDate date = LocalDate.of(2026, 10, day);
      long id = jobs.run("DAILY", date);
      var job = jobs.get(id);
      assertEquals("SUCCESS", job.status(), String.valueOf(job.error()));
      assertEquals(id, jobs.run("DAILY", date));
      assertFalse(
          market
              .bars(
                  "300476.SZ",
                  DateRangeRequest.builder().start(date.minusYears(2)).end(date).build())
              .isEmpty());
    }
    assertTrue(
        market
                .bars(
                    "300476.SZ",
                    DateRangeRequest.builder()
                        .start(LocalDate.of(2024, 10, 30))
                        .end(LocalDate.of(2026, 10, 30))
                        .build())
                .size()
            >= 250);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM signal_event WHERE status='TRIGGERED'", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_log", Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM market_score WHERE state<>'UNKNOWN'", Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (SELECT instrument_id,trade_date,COUNT(*) n FROM daily_bar GROUP BY instrument_id,trade_date,source,is_synthetic HAVING n>1) d",
            Integer.class));
    long weekly = jobs.run("WEEKLY", LocalDate.of(2026, 10, 30));
    assertEquals("SUCCESS", jobs.get(weekly).status());
  }

  /** 验证 JavaBean 映射及统一 Result 外壳，公开标的仍隐藏持久化主键。 */
  @Test
  void holdingResearchIsReadOnlyTypedAndDemoCannotCreateTradePrices() throws Exception {
    int reports = jdbc.queryForObject("SELECT COUNT(*) FROM analysis_report", Integer.class);
    mvc.perform(get("/api/v1/instruments/300476.SZ/holding-research").param("asOf", "2026-10-30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.horizons.length()").value(3))
        .andExpect(jsonPath("$.data.horizons[0].status").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data.horizons[1].actions.length()").value(0))
        .andExpect(jsonPath("$.data.close").isEmpty());
    mvc.perform(get("/api/v1/instruments/300476.SZ/holding-research"))
        .andExpect(status().isBadRequest());
    assertEquals(
        reports, jdbc.queryForObject("SELECT COUNT(*) FROM analysis_report", Integer.class));
  }

  @Test
  void publicApiKeepsBusinessFieldsAndHidesPersistenceId() throws Exception {
    mvc.perform(get("/api/v1/instruments/300476.SZ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.symbol").value("300476.SZ"))
        .andExpect(jsonPath("$.data.name").value("胜宏科技"))
        .andExpect(jsonPath("$.data.active").value(true))
        .andExpect(jsonPath("$.data.id").doesNotExist());
    mvc.perform(get("/api/v1/watchlist"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].symbol").isString())
        .andExpect(jsonPath("$.data[0].enabled").isBoolean());
    mvc.perform(get("/api/v1/portfolio/positions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.currency").value("CNY"))
        .andExpect(jsonPath("$.data.account.dataStatus").value("INSUFFICIENT_DATA"));
    mvc.perform(get("/api/v1/instruments/999999.SZ"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").isString());
  }

  private DailyBarDto sample(String source, boolean demo, String factor) {
    var d = LocalDate.of(2000, 1, 4);
    return new DailyBarDto(
        "300476.SZ",
        d,
        new BigDecimal("10"),
        new BigDecimal("11"),
        new BigDecimal("9"),
        new BigDecimal("10"),
        new BigDecimal("100"),
        new BigDecimal("1000"),
        new BigDecimal(factor),
        source,
        d.atTime(16, 0),
        demo);
  }

  @Test
  void sourcesModesAndFactorOnlyRevisionsArePreserved() {
    long instrument = market.instrument("300476.SZ").getId();
    for (var bar :
        List.of(
            sample("TEST_A", false, "1"),
            sample("TEST_B", false, "1"),
            sample("TEST_A", true, "1")))
      writer.persist(instrument, new MarketWindow(List.of(bar), Map.of()));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM daily_bar WHERE instrument_id=? AND trade_date='2000-01-04'",
            Integer.class,
            instrument));
    var corrected = sample("TEST_A", false, "2");
    writer.persist(instrument, new MarketWindow(List.of(corrected), Map.of()));
    var barQuery =
        MarketBarQuery.builder()
            .symbol("300476.SZ")
            .startDate(corrected.tradeDate())
            .endDate(corrected.tradeDate());
    var realA = marketMapper.bars(barQuery.source("TEST_A").synthetic(false).build());
    var realB = marketMapper.bars(barQuery.source("TEST_B").synthetic(false).build());
    var demoA = marketMapper.bars(barQuery.source("TEST_A").synthetic(true).build());
    assertEquals(1, realA.size());
    assertEquals(1, realB.size());
    assertEquals(1, demoA.size());
    assertEquals(0, new BigDecimal("2").compareTo(realA.getFirst().getFactor()));
    assertEquals(0, BigDecimal.ONE.compareTo(realB.getFirst().getFactor()));
    assertEquals(0, BigDecimal.ONE.compareTo(demoA.getFirst().getFactor()));
    assertFalse(realA.getFirst().getSynthetic());
    assertTrue(demoA.getFirst().getSynthetic());
    assertTrue(
        marketMapper
            .bars(
                barQuery
                    .startDate(corrected.tradeDate().plusDays(1))
                    .endDate(corrected.tradeDate().plusDays(1))
                    .build())
            .isEmpty());
    assertEquals(
        4,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM market_data_revision WHERE instrument_id=? AND trade_date='2000-01-04'",
            Integer.class,
            instrument));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM daily_bar_audit WHERE instrument_id=? AND trade_date='2000-01-04' AND JSON_EXTRACT(old_value,'$.factor')=1 AND JSON_EXTRACT(new_value,'$.factor')=2",
            Integer.class,
            instrument));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT factor FROM adjustment_factor WHERE instrument_id=? AND trade_date='2000-01-04' AND source='TEST_A' AND is_synthetic=1",
            Integer.class,
            instrument));
  }

  @Test
  void abandonedJobRetriesWithSameIdAndPreservesExecutionHistory() {
    jdbc.update(
        "INSERT INTO job_execution(job_name,business_date,attempt,status,started_at,data_scope,heartbeat_at,owner_id) VALUES('DAILY','2001-01-02',1,'RUNNING',DATE_SUB(NOW(),INTERVAL 10 MINUTE),'DEMO',DATE_SUB(NOW(),INTERVAL 10 MINUTE),'dead-worker')");
    long id = jdbc.queryForObject("SELECT MAX(id) FROM job_execution", Long.class);
    jdbc.update(
        "INSERT INTO job_stage(job_id,stage_name,status,started_at) VALUES(?,'CALENDAR','RUNNING',NOW())",
        id);
    jobs.recover();
    assertEquals("INTERRUPTED", jobs.get(id).status());
    assertEquals(
        "INTERRUPTED",
        jdbc.queryForObject("SELECT status FROM job_stage WHERE job_id=?", String.class, id));
    long retry = jobs.retry(id);
    assertEquals(id, retry);
    assertEquals("PENDING", jobs.get(id).status());
    assertNull(jobs.get(id).error());
    assertEquals(0, jobs.get(id).stages().size());
    assertEquals(id, jobs.retry(id), "排队中的重复重试必须幂等");
    var history = jobs.history(id, new com.alphascope.common.dto.PageRequest());
    assertEquals(1, history.total());
    assertEquals("INTERRUPTED", history.items().getFirst().status());
    assertEquals("INTERRUPTED", history.items().getFirst().stages().getFirst().status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM job_execution WHERE business_date='2001-01-02' AND job_name='DAILY'",
            Integer.class));
    jobs.process(id);
    assertEquals("SUCCESS", jobs.get(id).status());
    assertNull(jobs.get(id).error());
    assertEquals(
        "INTERRUPTED",
        jobs.history(id, new com.alphascope.common.dto.PageRequest()).items().getFirst().status());
    var query = new com.alphascope.scheduler.dto.JobQuery();
    query.setType("DAILY");
    assertTrue(jobs.page(query).items().stream().allMatch(x -> x.jobName().equals("DAILY")));
    query.setType("WEEKLY");
    assertTrue(jobs.page(query).items().stream().noneMatch(x -> x.id() == id));
  }

  @Test
  void updatedWatchlistCreatesNewInputVersionAndReportArchiveIsTyped() throws Exception {
    var date = LocalDate.of(2026, 10, 30);
    long first = jobs.run("DAILY", date);
    assertEquals("SUCCESS", jobs.get(first).status());
    assertEquals(first, jobs.run("DAILY", date));
    jdbc.update(
        "UPDATE watchlist SET priority=priority+1 WHERE instrument_id=?",
        market.instrument("300476.SZ").getId());
    long changed = jobs.run("DAILY", date);
    assertNotEquals(first, changed);
    assertEquals("SUCCESS", jobs.get(changed).status());
    long a = reports.generate("DAILY", date), b = reports.generate("DAILY", date);
    var report = reports.get(b);
    assertEquals("ADJ_V2/RULE_V2/REPORT_V12", report.algorithmVersion());
    assertEquals(
        report.algorithmVersion(), report.inputSnapshot().path("algorithmVersion").asText());
    assertEquals(
        "REPORT_V12",
        jdbc.queryForObject(
            "SELECT prompt_version FROM analysis_report WHERE id=?", String.class, b));
    assertTrue(report.contentJson().path("summary").asText().contains("300476.SZ"));
    assertTrue(report.inputSnapshot().has("watchlist"));
    assertEquals("PRICE_VOLUME_2M_V3", report.inputSnapshot().path("priceVolumeMethod").asText());
    assertNotNull(report.analysis().priceVolume());
    assertEquals(4, report.analysis().priceVolume().getFirst().strategies().size());
    assertTrue(
        report.inputSnapshot().path("watchlist").get(0).path("expectedTradingDates").isArray());
    assertEquals(report.inputSnapshot().path("analysis"), json.valueToTree(report.analysis()));
    assertTrue(report.inputSnapshot().path("ruleParameters").path("marginDrop").isNumber());
    assertEquals(date.atTime(15, 0), report.dataAsOf());
    assertNotNull(report.collectedAt());
    assertFalse(report.sources().isEmpty());
    assertEquals(
        1,
        reports
            .list(
                ReportSearchRequest.builder()
                    .type("DAILY")
                    .start(date)
                    .end(date)
                    .page(1)
                    .size(20)
                    .latestOnly(true)
                    .build())
            .items()
            .size());
    assertTrue(
        reports
                .list(
                    ReportSearchRequest.builder()
                        .type("DAILY")
                        .start(date)
                        .end(date)
                        .page(1)
                        .size(1)
                        .latestOnly(false)
                        .build())
                .total()
            >= 2);
    assertFalse(reports.compare(a, b).inputChanged());
    mvc.perform(get("/api/v1/reports?start=2026-10-30&end=2026-10-30&latestOnly=false&size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].business_date").value("2026-10-30"))
        .andExpect(jsonPath("$.data.total").isNumber());
    mvc.perform(get("/api/v1/reports/" + b))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content_json.summary").isString())
        .andExpect(jsonPath("$.data.input_snapshot.watchlist").isArray());
    mvc.perform(get("/api/v1/admin/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber());
  }
}
