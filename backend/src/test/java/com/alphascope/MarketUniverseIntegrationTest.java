package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.market.mapper.MarketUniverseMapper;
import com.alphascope.market.provider.research.SinaUniverseProvider;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 隔离MySQL验收北交所目录的盘中任务、不可变归档、只读查询及管理员边界。
 *
 * @author chenxuefeng
 */
@SpringBootTest(
    properties = {
      "alphascope.provider=multi",
      "alphascope.market.channels=sina",
      "alphascope.jobs.worker-enabled=false",
      "alphascope.scheduler.enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
@Import(MarketUniverseIntegrationTest.FixedClock.class)
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class MarketUniverseIntegrationTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.40");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-17T06:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate DATE = LocalDate.of(2026, 11, 17);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.flyway.user", () -> "root");
    registry.add("spring.flyway.password", MYSQL::getPassword);
  }

  /**
   * 将本用例固定为盘中，不根据执行机器时钟决定是否放行收盘任务。
   *
   * @author chenxuefeng
   */
  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock fixtureClock() {
      return CLOCK;
    }
  }

  @MockitoBean SinaUniverseProvider provider;
  @MockitoBean MarketService market;
  @Autowired MarketUniverseService universe;
  @Autowired MarketUniverseMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JobService jobs;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void before() {
    when(market.synthetic()).thenReturn(false);
    when(market.dataScope()).thenReturn("REAL:SINA");
    when(market.provider()).thenReturn("SINA");
    when(provider.fetch(DATE))
        .thenAnswer(ignored -> new SinaUniverseProvider(CLOCK, this::response).fetch(DATE));
  }

  private JsonNode response(URI uri) {
    if (uri.toString().contains("StockCount")) return json.getNodeFactory().textNode("4000");
    int page = Integer.parseInt(uri.getQuery().split("page=")[1].split("&")[0]);
    var rows = json.createArrayNode();
    for (int i = (page - 1) * 100; i < page * 100; i++) {
      String symbol =
          i < 2
              ? "bj" + (920001 + i)
              : i < 2000
                  ? "sh" + (600000 + i - 2)
                  : "sz" + String.format(java.util.Locale.ROOT, "%06d", i - 2000);
      rows.add(
          json.createObjectNode()
              .put("symbol", symbol)
              .put("code", symbol.substring(2))
              .put("name", "测试" + i));
    }
    return rows;
  }

  @Test
  void intradayJobArchivesExactDateWithoutCreatingPricesReportsOrWatchlistRows() throws Exception {
    int watches = jdbc.queryForObject("SELECT COUNT(*) FROM watchlist", Integer.class);
    int reports = jdbc.queryForObject("SELECT COUNT(*) FROM analysis_report", Integer.class);
    var response =
        mvc.perform(post("/api/v1/admin/jobs/market-universe").param("date", DATE.toString()))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(response).path("data").path("id").asLong();
    jobs.process(id);
    var job = jobs.get(id);
    assertEquals("SUCCESS", job.status());
    assertEquals("AVAILABLE", job.dataStatus());
    var result = universe.snapshot(DATE);
    assertEquals(2, result.memberCount());
    assertEquals("AVAILABLE", result.status());
    var raw = mapper.evidence(result.snapshotId());
    assertEquals(
        result.contentHash(), Jsons.hash(raw.getMembersJson() + "\n" + raw.getEvidenceJson()));
    universe.sync(DATE);
    verify(provider, times(1)).fetch(DATE);
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM market_universe_snapshot", Integer.class));
    assertEquals(watches, jdbc.queryForObject("SELECT COUNT(*) FROM watchlist", Integer.class));
    assertEquals(
        reports, jdbc.queryForObject("SELECT COUNT(*) FROM analysis_report", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM daily_bar", Integer.class));
    mvc.perform(get("/api/v1/market/universe").param("date", DATE.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberCount").value(2));
    mvc.perform(get("/api/v1/market/universe").param("date", DATE.minusDays(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("NOT_ARCHIVED"));
    verify(provider, times(1)).fetch(any());
  }

  @Test
  void ordinaryUserCannotSubmitSharedCollection() {
    try (var ignored = TenantContext.bind(new UserView(1, "fixture-user", "USER"))) {
      var error =
          assertThrows(
              com.alphascope.common.exception.BusinessException.class,
              () ->
                  jobs.submit(
                      JobSubmissionCommand.builder()
                          .reportType("MARKET_UNIVERSE")
                          .businessDate(DATE)
                          .force(true)
                          .build()));
      assertTrue(error.getMessage().contains("管理员"));
      verifyNoInteractions(provider);
    }
  }
}
