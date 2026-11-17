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

  @Autowired com.alphascope.planning.mapper.ResearchPlanMapper planMapper;

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
        .andExpect(jsonPath("$.data.items[0].id").value(id));
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
          "INSERT INTO research_plan(tenant_id,symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES(?,?,'SHORT','TEST',?,REPEAT('a',64))",
          alice.user.id(),
          i % 2 == 0 ? "600000.SH" : "000001.SZ",
          snapshot);
    long id =
        fixtureJdbc.queryForObject(
            "SELECT MAX(id) FROM research_plan WHERE tenant_id=?", Long.class, alice.user.id());
    for (int i = 0; i < 25; i++)
      fixtureJdbc.update(
          "INSERT INTO research_plan_review(tenant_id,plan_id,review_json,review_hash) VALUES(?,?,?,REPEAT('b',64))",
          alice.user.id(),
          id,
          "{}");
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
