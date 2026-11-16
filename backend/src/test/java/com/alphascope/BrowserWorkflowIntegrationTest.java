package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.ValidationSnapshot;
import com.alphascope.backtest.entity.ValidationExperiment;
import com.alphascope.backtest.mapper.ExperimentMapper;
import com.alphascope.common.task.TaskStopReason;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.JsonFingerprint;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.provider.DemoMarketDataProvider;
import com.alphascope.market.provider.MarketDataProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 真正Chrome、Vite、Spring和隔离MySQL的完整用户流程；不连接用户业务库或真实上游。
 *
 * @author chenxuefeng
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "alphascope.provider=demo",
      "alphascope.operations.backup-enabled=false",
      "alphascope.scheduler.enabled=false",
      "spring.ai.model.chat=none"
    })
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(BrowserWorkflowIntegrationTest.FixtureConfig.class)
@EnabledIfEnvironmentVariable(named = "RUN_BROWSER_TESTS", matches = "true")
class BrowserWorkflowIntegrationTest {
  private static final int FRONTEND_PORT = freePort();

  private static int freePort() {
    try (var socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.40").withDatabaseName("alphascope_browser_fixture");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("alphascope.web.allowed-origins", () -> "http://127.0.0.1:" + FRONTEND_PORT);
    properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
    properties.add("spring.datasource.username", MYSQL::getUsername);
    properties.add("spring.datasource.password", MYSQL::getPassword);
  }

  @LocalServerPort int port;
  @Autowired ExperimentMapper experiments;

  @Autowired
  java.util.List<org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler> schedulers;

  @org.junit.jupiter.api.AfterEach
  void stopFixtureSchedulers() {
    schedulers.forEach(scheduler -> scheduler.shutdown());
  }

  @Test
  void submissionCancellationRetryReportsAndOperationsWorkInARealBrowser() throws Exception {
    var request =
        SignalStatisticsRequest.builder()
            .symbol("300476.SZ")
            .start(LocalDate.of(2000, 1, 4))
            .end(LocalDate.of(2000, 1, 4))
            .build();
    var input =
        SignalEvaluationInput.builder()
            .request(request)
            .bars(List.of())
            .benchmark(List.of())
            .signals(List.of())
            .tradingDates(Set.of())
            .minimumSamples(20)
            .build();
    var result = SignalEvaluationCalculator.evaluate(input);
    var snapshot =
        new ValidationSnapshot(input, null, Map.of(), Instant.now(), result.methodVersion());
    var row = new ValidationExperiment();
    row.setSymbol(request.getSymbol());
    row.setDataScope("ISOLATED_TEST_FIXTURE");
    row.setAvailabilityMode("RETROSPECTIVE");
    row.setMethodVersion(result.methodVersion());
    row.setRequestJson(Jsons.write(request));
    row.setSnapshotJson(Jsons.write(snapshot));
    row.setResultJson(Jsons.write(result));
    row.setInputHash(JsonFingerprint.hash(row.getSnapshotJson()));
    row.setResultHash(JsonFingerprint.hash(row.getResultJson()));
    experiments.insert(row);
    Path root = Path.of("..").toAbsolutePath().normalize();
    if (!Files.isDirectory(root.resolve("frontend"))) {
      root = Path.of(".").toAbsolutePath().normalize();
    }
    Path output = root.resolve("backend/target/browser-test");
    Files.createDirectories(output);
    ProcessBuilder builder =
        new ProcessBuilder(System.getenv().getOrDefault("NODE_BINARY", "node"), "e2e/workflow.mjs")
            .directory(root.resolve("frontend").toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.resolve("runner.log").toFile());
    builder.environment().put("E2E_BACKEND_URL", "http://127.0.0.1:" + port);
    builder.environment().put("E2E_WEB_PORT", String.valueOf(FRONTEND_PORT));
    builder.environment().put("E2E_EXPERIMENT_ID", row.getId().toString());
    builder.environment().put("E2E_ARTIFACT_DIR", output.toString());
    Process process = builder.start();
    try {
      assertTrue(process.waitFor(180, TimeUnit.SECONDS), "浏览器流程超时，详见target/browser-test");
      assertEquals(
          0,
          process.exitValue(),
          () -> {
            try {
              return Files.readString(output.resolve("runner.log"));
            } catch (Exception e) {
              return e.getClass().getSimpleName();
            }
          });
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  /**
   * 只对本测试提供可取消的慢日历，不在生产增加测试钩子。
   *
   * @author chenxuefeng
   */
  @TestConfiguration
  static class FixtureConfig {
    @Bean
    @Primary
    MarketDataProvider browserFixtureProvider() {
      return new SlowDemoProvider();
    }
  }

  /**
   * 第一轮日历暂停以稳定覆盖RUNNING取消，后续重试使用正常演示协议。
   *
   * @author chenxuefeng
   */
  static class SlowDemoProvider extends DemoMarketDataProvider {
    private final AtomicBoolean first = new AtomicBoolean(true);

    @Override
    public Map<LocalDate, Boolean> fetchCalendar(LocalDate start, LocalDate end) {
      if (first.compareAndSet(true, false)) {
        try {
          // 必须通过真实取消中断，不能因20秒自然结束而与UI准备速度竞态。
          Thread.sleep(120000);
          throw new IllegalStateException("浏览器取消场景未中断测试日历");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new TaskStoppedException(TaskStopReason.INTERRUPTED);
        }
      }
      return super.fetchCalendar(start, end);
    }
  }
}
