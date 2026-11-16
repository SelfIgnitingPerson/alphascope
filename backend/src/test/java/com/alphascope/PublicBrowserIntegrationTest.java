package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.provider.InstrumentDirectoryProvider;
import com.alphascope.operations.service.SourceLicenseService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTPS真实浏览器验证两个PUBLIC账号的完整使用闭环；凭据与证书只属于一次性测试。
 *
 * @author chenxuefeng
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "alphascope.provider=demo",
      "alphascope.access.mode=PUBLIC",
      "alphascope.operations.backup-enabled=false",
      "alphascope.access.bootstrap-username=fixture-admin",
      "alphascope.access.bootstrap-password=test-fixture-password-only",
      "alphascope.access.registration-enabled=true",
      "alphascope.access.invitation=test-fixture-invitation-with-more-than-24-chars"
    })
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_BROWSER_TESTS", matches = "true")
class PublicBrowserIntegrationTest {
  private static final int WEB_PORT = freePort();

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.40").withDatabaseName("alphascope_public_browser_fixture");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("alphascope.access.origins", () -> "https://127.0.0.1:" + WEB_PORT);
    properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
    properties.add("spring.datasource.username", MYSQL::getUsername);
    properties.add("spring.datasource.password", MYSQL::getPassword);
  }

  private static int freePort() {
    try (var socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @LocalServerPort int port;
  @TempDir Path temporary;
  @MockitoBean SourceLicenseService licenses;
  @MockitoBean InstrumentDirectoryProvider directory;

  @Autowired
  java.util.List<org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler> schedulers;

  @org.junit.jupiter.api.AfterEach
  void stopWorkers() {
    schedulers.forEach(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler::shutdown);
  }

  @Test
  void secureBrowserOnboardingAndResearchRemainPrivateAfterAccountSwitching() throws Exception {
    Path root = Path.of("..").toAbsolutePath().normalize();
    if (!Files.isDirectory(root.resolve("frontend")))
      root = Path.of(".").toAbsolutePath().normalize();
    Path output = root.resolve("backend/target/public-browser-test");
    Files.createDirectories(output);
    Path key = temporary.resolve("fixture-key.pem"),
        certificate = temporary.resolve("fixture-cert.pem");
    var openssl =
        new ProcessBuilder(
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-nodes",
                "-days",
                "1",
                "-subj",
                "/CN=127.0.0.1",
                "-addext",
                "subjectAltName=IP:127.0.0.1",
                "-keyout",
                key.toString(),
                "-out",
                certificate.toString())
            .redirectErrorStream(true)
            .redirectOutput(temporary.resolve("certificate.log").toFile())
            .start();
    assertTrue(openssl.waitFor(20, TimeUnit.SECONDS));
    assertEquals(0, openssl.exitValue(), "需要OpenSSL生成一次性HTTPS测试证书");
    var builder =
        new ProcessBuilder(System.getenv().getOrDefault("NODE_BINARY", "node"), "e2e/workflow.mjs")
            .directory(root.resolve("frontend").toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.resolve("runner.log").toFile());
    builder.environment().put("E2E_PUBLIC", "true");
    builder.environment().put("E2E_BACKEND_URL", "http://127.0.0.1:" + port);
    builder.environment().put("E2E_WEB_PORT", Integer.toString(WEB_PORT));
    builder.environment().put("E2E_TLS_KEY", key.toString());
    builder.environment().put("E2E_TLS_CERT", certificate.toString());
    builder.environment().put("E2E_ARTIFACT_DIR", output.toString());
    var maximumHeap = new java.util.concurrent.atomic.AtomicLong();
    var sampling = new java.util.concurrent.atomic.AtomicBoolean(true);
    Thread sampler =
        Thread.startVirtualThread(
            () -> {
              while (sampling.get()) {
                maximumHeap.accumulateAndGet(
                    java.lang.management.ManagementFactory.getMemoryMXBean()
                        .getHeapMemoryUsage()
                        .getUsed(),
                    Math::max);
                try {
                  Thread.sleep(20);
                } catch (InterruptedException error) {
                  return;
                }
              }
            });
    Process browser = builder.start();
    try {
      assertTrue(browser.waitFor(240, TimeUnit.SECONDS), "PUBLIC浏览器测试超时");
      assertEquals(
          0,
          browser.exitValue(),
          () -> {
            try {
              return Files.readString(output.resolve("runner.log"));
            } catch (Exception error) {
              return error.getClass().getSimpleName();
            }
          });
    } finally {
      if (browser.isAlive()) browser.destroyForcibly();
      sampling.set(false);
      sampler.interrupt();
      sampler.join(1000);
      Files.writeString(
          output.resolve("memory.json"),
          "{\"observedJvmHeapPeakBytes\":" + maximumHeap.get() + ",\"samplingMillis\":20}");
    }
  }
}
