package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.backtest.ResearchValidationFixtures;
import com.alphascope.backtest.dto.ValidationTaskMutation;
import com.alphascope.backtest.mapper.ValidationTaskMapper;
import com.alphascope.backtest.service.ResearchValidationService;
import com.alphascope.backtest.service.ValidationTaskService;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.market.entity.MarketScore;
import com.alphascope.market.mapper.ScoreMapper;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.ProviderStatusView;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.service.PortfolioService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 隔离MySQL核验验证队列去重、取消、截止、失联和最终发布围栏，不接入当前业务库。
 *
 * @author chenxuefeng
 */
@SpringBootTest(
    properties = {
      "alphascope.provider=demo",
      "alphascope.jobs.worker-enabled=false",
      "alphascope.scheduler.enabled=false",
      "alphascope.operations.backup-enabled=false"
    })
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class ValidationTaskIntegrationTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.40").withDatabaseName("validation_queue_fixture");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired ValidationTaskService tasks;
  @Autowired ValidationTaskMapper mapper;
  @Autowired ScoreMapper scores;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean ResearchValidationService validation;
  @MockitoBean MarketService market;
  @MockitoBean PortfolioService portfolio;

  @BeforeEach
  void prepare() {
    jdbc.update("DELETE FROM research_validation_task");
    reset(validation, market, portfolio);
    when(market.synthetic()).thenReturn(false);
    when(market.providerStatus())
        .thenReturn(
            new ProviderStatusView(
                "TEST_FIXTURE",
                "REAL:TEST_FIXTURE",
                false,
                "TEST_FIXTURE",
                List.of(),
                List.of(),
                List.of()));
    var watch = new WatchlistDO();
    watch.setSymbol("300476.SZ");
    watch.setEnabled(true);
    when(portfolio.watchlist()).thenReturn(List.of(watch));
    when(validation.validate(any()))
        .thenAnswer(call -> ResearchValidationFixtures.report(call.getArgument(0)));
  }

  @Test
  void sameActiveRequestIsDeduplicatedAndSuccessPublishesOnlySmallSummary() {
    var request = ResearchValidationFixtures.command().request();
    var first = tasks.submit(request);
    var duplicate = tasks.submit(request);
    assertEquals(first.id(), duplicate.id());
    assertEquals(1, mapper.count());
    tasks.processNext();
    var done = tasks.get(first.id());
    assertEquals("SUCCESS", done.status());
    assertNotNull(done.summary());
    assertTrue(done.summary().verification().matched());
    String summary =
        jdbc.queryForObject(
            "SELECT summary_json FROM research_validation_task WHERE id=?",
            String.class,
            first.id());
    assertFalse(summary.contains("inputJson"));
    assertFalse(summary.contains("indicatorJson"));
    assertFalse(summary.contains("signalJson"));
    assertNotNull(tasks.evidence(first.id()));
    assertNull(tasks.page(PageRequest.builder().build()).items().getFirst().summary());
    assertNotEquals(first.id(), tasks.submit(request).id(), "终态后允许明确提交新的验证");
  }

  @Test
  void queuedCancellationNeverRunsAndRunningCancellationCannotPublishEvidence() throws Exception {
    var queued = tasks.submit(ResearchValidationFixtures.command().request());
    assertEquals("CANCELLED", tasks.cancel(queued.id()).status());
    tasks.processNext();
    verify(validation, never()).validate(any());
    var started = new CountDownLatch(1);
    doAnswer(
            call -> {
              started.countDown();
              try {
                Thread.sleep(120000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              ExecutionContext.checkpoint();
              throw new AssertionError("取消应中断执行");
            })
        .when(validation)
        .validate(any());

    var task = tasks.submit(ResearchValidationFixtures.command().request());
    try (var executor = Executors.newSingleThreadExecutor()) {
      var future = executor.submit(tasks::processNext);
      assertTrue(started.await(5, TimeUnit.SECONDS));
      tasks.cancel(task.id());
      future.get(5, TimeUnit.SECONDS);
    }
    assertEquals("CANCELLED", tasks.get(task.id()).status());
    assertNull(mapper.evidence(task.id()));
    assertThrows(
        com.alphascope.common.exception.BusinessException.class, () -> tasks.evidence(task.id()));
  }

  @Test
  void finalSqlKeepsPersistedStopAndDeadlineInsteadOfSuccess() {
    var first = tasks.submit(ResearchValidationFixtures.command().request());
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    assertEquals(
        1,
        mapper.claim(
            ValidationTaskMutation.builder()
                .id(first.id())
                .owner("fixture-owner")
                .now(now)
                .deadline(now.plusMinutes(5))
                .build()));
    mapper.cancel(ValidationTaskMutation.builder().id(first.id()).now(now).build());
    assertEquals(
        0,
        mapper.finish(
            ValidationTaskMutation.builder()
                .id(first.id())
                .owner("wrong-owner")
                .now(now)
                .status("SUCCESS")
                .evidence("{}")
                .build()));
    assertEquals(
        1,
        mapper.finish(
            ValidationTaskMutation.builder()
                .id(first.id())
                .owner("fixture-owner")
                .now(now)
                .status("SUCCESS")
                .evidence("{}")
                .summary("{}")
                .evidenceHash("x")
                .build()));
    assertEquals("CANCELLED", tasks.get(first.id()).status());
    assertNull(mapper.evidence(first.id()));
    var second = tasks.submit(ResearchValidationFixtures.command().request());
    mapper.claim(
        ValidationTaskMutation.builder()
            .id(second.id())
            .owner("expired-owner")
            .now(now.minusMinutes(3))
            .deadline(now.minusMinutes(1))
            .build());
    mapper.finish(
        ValidationTaskMutation.builder()
            .id(second.id())
            .owner("expired-owner")
            .now(now)
            .status("SUCCESS")
            .evidence("{}")
            .summary("{}")
            .evidenceHash("x")
            .build());
    assertEquals("TIMED_OUT", tasks.get(second.id()).status());
    assertNull(mapper.evidence(second.id()));
  }

  @Test
  void lostProcessIsRecoveredAndCorruptedEvidenceIsRejected() {
    var first = tasks.submit(ResearchValidationFixtures.command().request());
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    mapper.claim(
        ValidationTaskMutation.builder()
            .id(first.id())
            .owner("lost-owner")
            .now(now.minusMinutes(5))
            .deadline(now.plusMinutes(5))
            .build());
    tasks.monitor();
    assertEquals("INTERRUPTED", tasks.get(first.id()).status());
    var next = tasks.submit(ResearchValidationFixtures.command().request());
    tasks.processNext();
    jdbc.update("UPDATE research_validation_task SET evidence_json='{}' WHERE id=?", next.id());
    assertThrows(IllegalStateException.class, () -> tasks.evidence(next.id()));
  }

  @Test
  void marketScoreQueryIncludesActualDatabaseTimestamps() {
    var date = java.time.LocalDate.of(2025, 1, 2);
    scores.save(
        MarketScore.builder()
            .tradeDate(date)
            .modelVersion("MARKET_V3")
            .score(new java.math.BigDecimal("75"))
            .state("POSITIVE")
            .evidenceJson("{}")
            .synthetic(false)
            .build());
    var row = scores.scores(date, date, false).getFirst();
    assertNotNull(row.getCreatedAt());
    assertNotNull(row.getUpdatedAt());
    assertNotNull(row.getId());
    assertEquals(Boolean.FALSE, row.getSynthetic());
  }
}
