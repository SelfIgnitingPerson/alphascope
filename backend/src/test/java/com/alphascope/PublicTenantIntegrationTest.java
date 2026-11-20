package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.context.TenantExecutor;
import com.alphascope.identity.vo.UserView;
import com.alphascope.intelligence.dto.ReportSearchRequest;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 真实MySQL下经过HTTP会话、CSRF、角色与SQL隔离验证两个独立用户及后台执行。
 *
 * @author chenxuefeng
 */
@SpringBootTest(
    properties = {
      "alphascope.provider=demo",
      "alphascope.jobs.worker-enabled=false",
      "alphascope.access.mode=PUBLIC",
      "alphascope.access.origins=https://research.example.test",
      "alphascope.access.bootstrap-username=admin",
      "alphascope.access.bootstrap-password=test-fixture-password-only",
      "alphascope.access.registration-enabled=true",
      "alphascope.access.invitation=test-fixture-invitation-with-more-than-24-chars"
    })
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PublicTenantIntegrationTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.40");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
    properties.add("spring.datasource.username", MYSQL::getUsername);
    properties.add("spring.datasource.password", MYSQL::getPassword);
    properties.add("spring.flyway.user", () -> "root");
    properties.add("spring.flyway.password", MYSQL::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PortfolioService portfolio;
  @Autowired JobService jobs;
  @Autowired ReportService reports;
  @Autowired TenantExecutor tenants;
  @Autowired com.alphascope.operations.mapper.PlatformCapacityMapper platform;
  @MockitoBean SourceLicenseService licenses;

  @Autowired com.alphascope.planning.mapper.ResearchPlanMapper planMapper;
  @Autowired com.alphascope.planning.service.ResearchPlanService plans;
  @Autowired com.alphascope.fundamental.service.InvestmentEvidenceService investmentEvidence;

  @Autowired com.alphascope.fundamental.mapper.FinancialSyncMapper financialSyncMapper;
  @Autowired com.alphascope.fundamental.service.FundamentalService financials;
  @Autowired com.alphascope.market.service.MarketService market;
  @Autowired javax.sql.DataSource source;
  @Autowired com.alphascope.intelligence.mapper.ResearchObservationMapper observationMapper;
  @Autowired com.alphascope.intelligence.mapper.ForwardResultMapper forwardMapper;

  @Test
  void exactObservationAndForwardPayloadsRoundTripWithoutJsonNormalizationOrTenantLeakage()
      throws Exception {
    Actor alice = register("exactalice"), bob = register("exactbob");
    Long observationId;
    String raw = "{ \"x\":12345678901234567890.1234, \"research\": {\"version\":\"test\"} }";
    try (var ignored = TenantContext.bind(alice.user)) {
      var row = new com.alphascope.intelligence.entity.ResearchObservation();
      row.setSymbol("300476.SZ");
      row.setBusinessDate(LocalDate.of(2026, 10, 30));
      row.setCaptureKind("LIVE");
      row.setEvidenceJson(raw);
      row.setEvidenceHash(com.alphascope.common.util.Jsons.hash(raw));
      assertEquals(1, observationMapper.save(row));
      var query =
          new com.alphascope.intelligence.dto.ObservationQuery(
              null, LocalDate.now().plusDays(1), java.time.LocalDateTime.now().plusDays(1));
      var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
      observationId =
          jdbc.queryForObject(
              "SELECT id FROM research_observation WHERE tenant_id=? AND evidence_hash=?",
              Long.class,
              alice.user.id(),
              row.getEvidenceHash());
      assertEquals(
          observationId,
          observationMapper
              .forward(
                  new com.alphascope.intelligence.dto.ObservationQuery(
                      "300476.SZ", query.date(), query.cutoff(), null, null))
              .getFirst()
              .getId());
      assertTrue(
          observationMapper
              .forward(
                  new com.alphascope.intelligence.dto.ObservationQuery(
                      "300476.SZ", query.date(), query.cutoff(), null, observationId))
              .isEmpty());
      assertEquals(
          raw,
          jdbc.queryForObject(
              "SELECT evidence_json FROM research_observation WHERE id=?",
              String.class,
              observationId));
      var result = new com.alphascope.intelligence.entity.ForwardResult();
      result.setObservationId(observationId);
      result.setActionCode("SHORT:OPEN_PULLBACK");
      result.setWindowDays(5);
      result.setMetricsJson(raw);
      result.setInputJson(raw);
      result.setMetricsHash(com.alphascope.common.util.Jsons.hash(raw));
      result.setContentHash(com.alphascope.common.util.Jsons.hash(raw + "\n" + raw));
      assertEquals(1, forwardMapper.save(result));
      var resultQuery =
          new com.alphascope.intelligence.dto.ForwardResultQuery(
              query.cutoff(), java.util.List.of(observationId));
      var read = forwardMapper.summaries(resultQuery).getFirst();
      assertTrue(
          forwardMapper
              .summaries(
                  new com.alphascope.intelligence.dto.ForwardResultQuery(
                      query.cutoff(), java.util.List.of()))
              .isEmpty());
      assertEquals(raw, read.getMetricsJson());
      assertEquals(result.getMetricsHash(), read.getMetricsHash());
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertTrue(
          forwardMapper
              .summaries(
                  new com.alphascope.intelligence.dto.ForwardResultQuery(
                      java.time.LocalDateTime.now().plusDays(1), java.util.List.of(observationId)))
              .isEmpty());
    }
  }

  @Test
  void financialSyncStoresExactThreeStatementEnvelopeAndItsUpstreamHash() throws Exception {
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
    jdbc.update(
        "INSERT INTO instrument(symbol,name,exchange,type,active) VALUES('600999.SH','精度测试股','SH','STOCK',1)");
    var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
    String raw =
        "{ \"income\":{\"BASIC_EPS\":1.23456789012345},\"cashflow\":{},\"balance\":{\"SHARE_CAPITAL\":123456789012345.1234} }";
    var statement =
        new com.alphascope.fundamental.provider.EastmoneyFinancialProvider.Statement(
            LocalDate.of(2026, 9, 30),
            "Q3_CUMULATIVE",
            now.minusDays(2),
            now.minusDays(1),
            now.minusDays(2),
            new java.math.BigDecimal("100"),
            new java.math.BigDecimal("10"),
            new java.math.BigDecimal("8"),
            new java.math.BigDecimal(".3"),
            null,
            null,
            "https://example.test/financial",
            raw,
            com.alphascope.common.util.Jsons.hash(raw));
    try (var ignored = TenantContext.bind(new UserView(1, "admin", "ADMIN"))) {
      financialSyncMapper.insert(market.instrument("600999.SH").getId(), statement, now);
      var record = financials.reports("600999.SH", now.toLocalDate().plusDays(1)).getFirst();
      assertEquals(raw, record.getRawPayload());
      assertEquals(
          record.getContentHash(), com.alphascope.common.util.Jsons.hash(record.getRawPayload()));
    }
  }

  @Test
  void positionUpdatesWriteHistoryAndBackfillsDoNotOverwriteCurrentOrLeakAcrossUsers()
      throws Exception {
    Actor alice = register("historyalice"), bob = register("historybob");
    var today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    try (var ignored = TenantContext.bind(alice.user)) {
      portfolio.position(
          "300476.SZ",
          new com.alphascope.portfolio.dto.PositionInput(
              new java.math.BigDecimal("100"), new java.math.BigDecimal("20"), today, "PCB,AI算力"));
      portfolio.position(
          "300476.SZ",
          new com.alphascope.portfolio.dto.PositionInput(
              new java.math.BigDecimal("200"), new java.math.BigDecimal("21"), today, "PCB,AI算力"));
      assertEquals(
          0,
          new java.math.BigDecimal("200")
              .compareTo(portfolio.context(today).positions().getFirst().getQuantity()));
      portfolio.position(
          "300476.SZ",
          new com.alphascope.portfolio.dto.PositionInput(
              new java.math.BigDecimal("999"),
              new java.math.BigDecimal("10"),
              today.minusDays(1),
              "事后补录"));
      assertEquals(
          0,
          new java.math.BigDecimal("200")
              .compareTo(portfolio.positions().getFirst().getQuantity()));
      assertTrue(
          portfolio.context(today.minusDays(1)).positions().isEmpty(), "今天补填的旧业务日不能进入昨天实际已知的持仓");
      assertTrue(portfolio.context(today).positions().getFirst().isHistoryVerified());
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertTrue(portfolio.context(today).positions().isEmpty());
    }
  }

  @Test
  void personalFundingContextRemainsPrivateAcrossTenants() throws Exception {
    Actor alice = register("contextalice"), bob = register("contextbob");
    try (var ignored = TenantContext.bind(alice.user)) {
      portfolio.account(
          new com.alphascope.portfolio.dto.AccountInput(
              new java.math.BigDecimal("100000"),
              LocalDate.of(2026, 10, 30),
              new java.math.BigDecimal("5000"),
              new java.math.BigDecimal("30000"),
              LocalDate.of(2027, 1, 1),
              "计划用款"));
      assertEquals(
          0, new java.math.BigDecimal("5000").compareTo(portfolio.account().getRiskBudget()));
      assertEquals("计划用款", portfolio.positionsView().account().fundsPurpose());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              portfolio.account(
                  new com.alphascope.portfolio.dto.AccountInput(
                      new java.math.BigDecimal("100"),
                      LocalDate.of(2026, 10, 30),
                      null,
                      new java.math.BigDecimal("101"),
                      null,
                      null)));
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertNull(portfolio.account().getRiskBudget());
      assertNull(portfolio.positionsView().account().fundsPurpose());
    }
  }

  @Test
  void planBusinessDatesDeduplicationAndValuationRemainTenantIsolated() throws Exception {
    Actor alice = register("planalice"), bob = register("planbob");
    long planId;
    try (var ignored = TenantContext.bind(alice.user)) {
      var plan = new com.alphascope.planning.entity.ResearchPlan();
      plan.setSymbol("300476.SZ");
      plan.setHorizon("SHORT");
      plan.setActionCode("OPEN_PULLBACK");
      plan.setSnapshotJson("{\"research\":{\"asOf\":\"2026-10-28\",\"name\":\"测试计划\"}}");
      plan.setSnapshotHash(com.alphascope.common.util.Jsons.hash(plan.getSnapshotJson()));
      planMapper.insert(plan);
      planId = plan.getId();
      assertEquals(planId, planMapper.identicalPlan(plan).getId());
      assertThrows(
          org.springframework.dao.DuplicateKeyException.class, () -> planMapper.insert(plan));
      for (var day : java.util.List.of("2026-10-29", "2026-10-30", "2026-10-28")) {
        var review = new com.alphascope.planning.entity.PlanReview();
        review.setPlanId(planId);
        review.setReviewJson(
            com.alphascope.common.util.Jsons.write(
                new com.alphascope.planning.vo.PlanReviewView(
                    planId,
                    LocalDate.parse(day),
                    day.endsWith("29") ? "ORIGINAL_STOP_BREACHED" : "DATA_INSUFFICIENT",
                    "日期证据",
                    90.0,
                    100.0,
                    "WAIT",
                    java.util.List.of())));
        review.setReviewHash(com.alphascope.common.util.Jsons.hash(review.getReviewJson()));
        planMapper.review(review);
        assertNotNull(planMapper.identicalReview(review));
        assertThrows(
            org.springframework.dao.DuplicateKeyException.class, () -> planMapper.review(review));
      }
      var query = new com.alphascope.planning.dto.PlanQuery();
      query.setAsOf(LocalDate.of(2026, 10, 29));
      assertEquals(
          LocalDate.of(2026, 10, 29), plans.page(query).items().getFirst().latestReviewDate());
      query.setAsOf(LocalDate.of(2026, 10, 30));
      assertEquals(
          LocalDate.of(2026, 10, 30), plans.page(query).items().getFirst().latestReviewDate());
      var originalRisk = plans.pageWithOriginalRisk(query).items().getFirst();
      assertEquals("ORIGINAL_STOP_BREACHED", originalRisk.latestReviewStatus());
      assertEquals(LocalDate.of(2026, 10, 29), originalRisk.latestReviewDate());
      plans.acknowledge(planId, new com.alphascope.planning.dto.PlanReviewInput(query.getAsOf()));
      assertTrue(plans.page(query).items().getFirst().acknowledged());
      query.setAsOf(LocalDate.of(2026, 10, 29));
      assertFalse(plans.page(query).items().getFirst().acknowledged());
      var thesis =
          new com.alphascope.fundamental.dto.ValuationThesisInput(
              "300476.SZ",
              LocalDate.of(2026, 10, 30),
              LocalDate.of(2026, 11, 30),
              java.math.BigDecimal.ONE,
              java.math.BigDecimal.valueOf(2),
              java.math.BigDecimal.TEN,
              java.math.BigDecimal.valueOf(20),
              java.math.BigDecimal.valueOf(.2),
              true,
              "人工核对年化每股收益与行业倍数，采用公开财报证据",
              "连续现金流恶化后撤销本情景",
              "https://example.test/financial",
              "EASTMONEY_FINANCIAL");
      investmentEvidence.saveThesis(thesis);
      assertEquals(
          thesis,
          investmentEvidence.thesis(
              new com.alphascope.fundamental.dto.EvidenceQuery(
                  "300476.SZ", LocalDate.of(2026, 10, 30))));
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertNull(planMapper.get(planId));
      assertTrue(
          planMapper
              .latestReviews(
                  new com.alphascope.planning.dto.PlanReviewBatchQuery(
                      java.util.List.of(planId), LocalDate.of(2026, 10, 30)))
              .isEmpty());
      assertNull(
          investmentEvidence.thesis(
              new com.alphascope.fundamental.dto.EvidenceQuery(
                  "300476.SZ", LocalDate.of(2026, 10, 30))));
    }
    mvc.perform(
            post("/api/v1/admin/corporate-actions")
                .cookie(alice.session)
                .header("Origin", "https://research.example.test")
                .header("X-CSRF-Token", alice.csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void publicSessionsRolesAndResearchStayIsolatedAcrossBackgroundExecution() throws Exception {
    mvc.perform(get("/api/v1/watchlist"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    Actor alice = register("alice"), bob = register("bob");
    assertNotEquals(alice.user.id(), bob.user.id());
    mvc.perform(get("/api/v1/watchlist").cookie(alice.session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    mvc.perform(
            post("/api/v1/watchlist")
                .cookie(alice.session)
                .header("Origin", "https://research.example.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"300476.SZ\",\"priority\":0}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/operations").cookie(alice.session))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/watchlist")
                .cookie(alice.session)
                .header("Origin", "https://attacker.example")
                .header("X-CSRF-Token", alice.csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"300476.SZ\",\"priority\":0}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/admin/market/providers/test")
                .cookie(alice.session)
                .header("Origin", "https://research.example.test")
                .header("X-CSRF-Token", alice.csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"SINA\",\"capability\":\"STOCK_DAILY\",\"date\":\"2026-10-30\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    long aliceJob, bobJob;
    try (var ignored = TenantContext.bind(alice.user)) {
      portfolio.watch("300476.SZ", 0);
      aliceJob =
          jobs.submit(
              JobSubmissionCommand.builder()
                  .reportType("DAILY")
                  .businessDate(LocalDate.of(2026, 10, 30))
                  .force(false)
                  .build());
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertEquals(0, portfolio.watchlist().size());
      portfolio.watch("002837.SZ", 0);
      assertThrows(
          com.alphascope.common.exception.NotFoundException.class, () -> jobs.get(aliceJob));
      bobJob =
          jobs.submit(
              JobSubmissionCommand.builder()
                  .reportType("DAILY")
                  .businessDate(LocalDate.of(2026, 10, 30))
                  .force(false)
                  .build());
      assertEquals(1, jobs.page(new PageRequest()).total());
      assertEquals(2, platform.usage().activeJobs(), "只有数字聚合例外能看到平台总量");
    }
    mvc.perform(
            post("/api/v1/admin/jobs/" + aliceJob + "/cancel")
                .cookie(bob.session)
                .header("Origin", "https://research.example.test")
                .header("X-CSRF-Token", bob.csrf))
        .andExpect(status().isNotFound());
    // Worker入口显式恢复每位已启用用户，同日报告、指标及规则唯一键不能串租户。
    tenants.each(jobs::processNext);
    long aliceReport;
    try (var ignored = TenantContext.bind(alice.user)) {
      assertEquals("SUCCESS", jobs.get(aliceJob).status(), jobs.get(aliceJob).error());
      assertEquals("300476.SZ", portfolio.watchlist().getFirst().getSymbol());
      assertEquals(
          aliceJob,
          jobs.submit(
              JobSubmissionCommand.builder()
                  .reportType("DAILY")
                  .businessDate(LocalDate.of(2026, 10, 30))
                  .automatic(true)
                  .build()),
          "自动调度只复用当前用户成功任务");
      var request = new ReportSearchRequest();
      request.setStart(LocalDate.of(2026, 10, 30));
      request.setEnd(LocalDate.of(2026, 10, 30));
      var page = reports.list(request);
      assertEquals(1, page.total());
      aliceReport = page.items().getFirst().id();
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertEquals("SUCCESS", jobs.get(bobJob).status());
      assertEquals("002837.SZ", portfolio.watchlist().getFirst().getSymbol());
      assertThrows(
          com.alphascope.common.exception.NotFoundException.class, () -> reports.get(aliceReport));
    }
    mvc.perform(get("/api/v1/reports/" + aliceReport).cookie(bob.session))
        .andExpect(status().isNotFound());
    // 令牌注销后即刻失效，不依赖浏览器本地状态。
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(alice.session)
                .header("Origin", "https://research.example.test")
                .header("X-CSRF-Token", alice.csrf))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/watchlist").cookie(alice.session))
        .andExpect(status().isUnauthorized());
    assertNull(TenantContext.user());
  }

  @Test
  void repeatedInvalidCredentialsAreThrottledUsingTheDatabaseTimeWindow() throws Exception {
    String payload = "{\"username\":\"limited-fixture\",\"password\":\"wrong-fixture-password\"}";
    for (int i = 0; i < 10; i++)
      mvc.perform(
              post("/api/v1/auth/login")
                  .header("Origin", "https://research.example.test")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload))
          .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", "https://research.example.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void researchFocusUsesOnlyAuthenticatedUsersEnabledWatches() throws Exception {
    var alice = register("focus-alice");
    var bob = register("focus-bob");
    try (var ignored = TenantContext.bind(alice.user())) {
      portfolio.watch("300476.SZ", 5);
    }
    mvc.perform(get("/api/v1/research-focus").param("asOf", "2026-11-17").cookie(alice.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].symbol").value("300476.SZ"));
    mvc.perform(get("/api/v1/research-focus").param("asOf", "2026-11-17").cookie(bob.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0))
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }

  @Test
  void personalPlansAndReviewsCannotBeReadOrArchivedAcrossUsers() throws Exception {
    var alice = register("plan-alice");
    var bob = register("plan-bob");
    var research =
        com.alphascope.intelligence.vo.HoldingResearchView.builder()
            .symbol("600000.SH")
            .name("隔离测试")
            .version("TEST_V1")
            .horizons(java.util.List.of())
            .build();
    String evidence =
        com.alphascope.common.util.Jsons.write(
            new com.alphascope.planning.dto.PlanEvidence(research, java.math.BigDecimal.ONE));
    var row = new com.alphascope.planning.entity.ResearchPlan();
    row.setSymbol("600000.SH");
    row.setHorizon("SHORT");
    row.setActionCode("OPEN_PULLBACK");
    row.setSnapshotJson(evidence);
    row.setSnapshotHash(com.alphascope.common.util.Jsons.hash(evidence));
    try (var ignored = com.alphascope.identity.context.TenantContext.bind(alice.user())) {
      planMapper.insert(row);
      var review = new com.alphascope.planning.entity.PlanReview();
      review.setPlanId(row.getId());
      review.setReviewJson(
          com.alphascope.common.util.Jsons.write(
              new com.alphascope.planning.vo.PlanReviewView(
                  row.getId(),
                  java.time.LocalDate.of(2026, 11, 16),
                  "UNCHANGED",
                  "测试复核",
                  null,
                  100.0,
                  "WAIT",
                  java.util.List.of())));
      review.setReviewHash(com.alphascope.common.util.Jsons.hash(review.getReviewJson()));
      planMapper.review(review);
    }
    Long id = row.getId();
    mvc.perform(get("/api/v1/plans/" + id + "/reviews").cookie(alice.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1));
    mvc.perform(get("/api/v1/plans").cookie(alice.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value(id))
        .andExpect(jsonPath("$.data.items[0].latestReviewDate").value("2026-11-16"))
        .andExpect(jsonPath("$.data.items[0].latestReviewStatus").value("UNCHANGED"));
    mvc.perform(get("/api/v1/plans").cookie(bob.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));
    mvc.perform(get("/api/v1/plans/" + id + "/reviews").cookie(bob.session()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/plans/" + id + "/archive")
                .cookie(bob.session())
                .header("Origin", "https://research.example.test")
                .header("X-CSRF-Token", bob.csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/admin/themes/CPO/leaders")
                .cookie(bob.session())
                .header("Origin", "https://research.example.test")
                .header("X-CSRF-Token", bob.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate fixtureJdbc;

  @Test
  void planAndReviewPaginationRetainsAllRecordsAndTenantBoundaries() throws Exception {
    var alice = register("plan-pages-a");
    var bob = register("plan-pages-b");
    String snapshot = "{\"research\":{\"name\":\"分页测试\",\"asOf\":\"2026-11-17\"}}";
    for (int i = 0; i < 121; i++)
      fixtureJdbc.update(
          "INSERT INTO research_plan(tenant_id,symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES(?,?,'SHORT','TEST',?,SHA2(?,256))",
          alice.user.id(),
          i % 2 == 0 ? "600000.SH" : "000001.SZ",
          snapshot,
          "plan-" + i);
    long id =
        fixtureJdbc.queryForObject(
            "SELECT MAX(id) FROM research_plan WHERE tenant_id=?", Long.class, alice.user.id());
    for (int i = 0; i < 25; i++)
      fixtureJdbc.update(
          "INSERT INTO research_plan_review(tenant_id,plan_id,review_json,review_hash) VALUES(?,?,?,SHA2(?,256))",
          alice.user.id(),
          id,
          "{}",
          "review-" + i);
    try (var ignored = TenantContext.bind(alice.user)) {
      var query = new com.alphascope.planning.dto.PlanQuery();
      query.setSize(20);
      assertEquals(121, planMapper.count(query));
      var ids = new java.util.HashSet<Long>();
      for (int page = 1; page <= 7; page++) {
        query.setPage(page);
        for (var row : planMapper.page(query)) assertTrue(ids.add(row.id()), "不能跨页重复");
      }
      assertEquals(121, ids.size());
      query.setSymbol("600000.SH");
      assertEquals(61, planMapper.count(query));
      assertEquals(
          20,
          planMapper.reviews(new com.alphascope.planning.dto.PlanReviewQuery(id, 20, 0)).size());
      assertEquals(
          5,
          planMapper.reviews(new com.alphascope.planning.dto.PlanReviewQuery(id, 20, 20)).size());
      assertEquals(25, planMapper.reviewCount(id));
    }
    try (var ignored = TenantContext.bind(bob.user)) {
      assertNull(planMapper.get(id));
      assertEquals(0, planMapper.reviewCount(id));
      assertTrue(
          planMapper.reviews(new com.alphascope.planning.dto.PlanReviewQuery(id, 20, 0)).isEmpty());
    }
    mvc.perform(get("/api/v1/plans/" + id).cookie(bob.session())).andExpect(status().isNotFound());
  }

  @Autowired com.alphascope.market.mapper.MarketBreadthMapper breadthMapper;
  @Autowired org.springframework.transaction.PlatformTransactionManager fixtureTransactions;

  @Test
  void fullSnapshotReadsLargeOriginalWithoutSortingItsPayload() {
    new org.springframework.transaction.support.TransactionTemplate(fixtureTransactions)
        .executeWithoutResult(
            tx -> {
              Integer old =
                  fixtureJdbc.queryForObject("SELECT @@session.sort_buffer_size", Integer.class);
              try {
                fixtureJdbc.execute("SET SESSION sort_buffer_size=32768");
                for (int i = 0; i < 2; i++) {
                  String raw = "{\"padding\":\"" + "x".repeat(1024 * 1024) + i + "\"}";
                  fixtureJdbc.update(
                      "INSERT INTO market_breadth_snapshot(trade_date,universe_scope,source,content_hash,metrics_json,raw_json,fetched_at) VALUES('2026-11-15','FULL_A','EASTMONEY',?,'{}',?,NOW())",
                      com.alphascope.common.util.Jsons.hash(raw),
                      raw);
                }
                var query =
                    com.alphascope.market.dto.ResearchSnapshotQuery.builder()
                        .date(LocalDate.of(2026, 11, 15))
                        .source("EASTMONEY")
                        .loadRaw(true)
                        .build();
                var row = breadthMapper.fullForDate(query);
                assertNotNull(row);
                assertTrue(row.getRawJson().length() > 1024 * 1024);
                assertTrue(row.getRawJson().endsWith("1\"}"));
                assertNull(
                    breadthMapper
                        .fullForDate(
                            com.alphascope.market.dto.ResearchSnapshotQuery.builder()
                                .date(query.date())
                                .source(query.source())
                                .loadRaw(false)
                                .build())
                        .getRawJson());
              } finally {
                fixtureJdbc.execute("SET SESSION sort_buffer_size=" + old);
                tx.setRollbackOnly();
              }
            });
  }

  private Actor register(String name) throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .header("Origin", "https://research.example.test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\""
                            + name
                            + "\",\"password\":\"test-only-long-password\",\"invitation\":\"test-fixture-invitation-with-more-than-24-chars\"}"))
            .andExpect(status().isOk())
            .andReturn();
    var node = json.readTree(result.getResponse().getContentAsString()).path("data");
    String session = cookie(result, "ALPHASCOPE_SESSION"), csrf = cookie(result, "ALPHASCOPE_CSRF");
    assertTrue(
        result.getResponse().getHeaders("Set-Cookie").stream()
            .anyMatch(
                value ->
                    value.contains("HttpOnly")
                        && value.contains("Secure")
                        && value.contains("SameSite=Strict")));
    return new Actor(
        new UserView(node.path("id").asLong(), name, "USER"),
        new Cookie("ALPHASCOPE_SESSION", session),
        csrf);
  }

  private String cookie(MvcResult result, String key) {
    return result.getResponse().getHeaders("Set-Cookie").stream()
        .filter(value -> value.startsWith(key + "="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0]
        .substring(key.length() + 1);
  }

  /**
   * 仅供隔离测试的浏览器凭据，不接触本机真实账号。
   *
   * @author chenxuefeng
   * @param user 服务器签发身份
   * @param session Cookie
   * @param csrf CSRF令牌
   */
  private record Actor(UserView user, Cookie session, String csrf) {}
}
