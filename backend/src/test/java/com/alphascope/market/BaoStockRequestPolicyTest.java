package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.provider.baostock.BaoStockRequestPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 隔离运行目录验证预算、跨客户端互斥和失败关闭，不访问供应商或业务数据库。
 *
 * @author chenxuefeng
 */
class BaoStockRequestPolicyTest {
  @TempDir Path directory;
  final Clock day = Clock.fixed(Instant.parse("2026-11-11T15:59:59Z"), ZoneOffset.UTC);

  @Test
  void budgetSurvivesRestartAndResetsOnlyAtShanghaiMidnight() {
    try (var first = new BaoStockRequestPolicy(directory, 1, day)) {
      first.acquireSession();
      first.beforeRequest();
      assertEquals(1, first.usage().requests());
      assertThrows(IllegalStateException.class, first::beforeRequest);
    }
    try (var next = new BaoStockRequestPolicy(directory, 1, day)) {
      next.acquireSession();
      assertThrows(IllegalStateException.class, next::beforeRequest);
    }
    try (var tomorrow =
        new BaoStockRequestPolicy(
            directory, 1, Clock.offset(day, java.time.Duration.ofSeconds(1)))) {
      tomorrow.acquireSession();
      tomorrow.beforeRequest();
      assertEquals("2026-11-12", tomorrow.usage().date());
    }
  }

  @Test
  void secondClientCannotOpenWhileFirstSessionIsAlive() {
    try (var first = new BaoStockRequestPolicy(directory, 10, day);
        var next = new BaoStockRequestPolicy(directory, 10, day)) {
      first.acquireSession();
      assertTrue(
          assertThrows(IllegalStateException.class, next::acquireSession)
              .getMessage()
              .contains("SESSION_BUSY"));
      first.close();
      next.acquireSession();
      next.beforeRequest();
      assertEquals(1, next.usage().requests());
    }
  }

  @Test
  void damagedOrFutureUsageNeverBecomesZero() throws Exception {
    Files.writeString(directory.resolve("usage.json"), "broken");
    try (var policy = new BaoStockRequestPolicy(directory, 10, day)) {
      policy.acquireSession();
      assertThrows(IllegalStateException.class, policy::beforeRequest);
      Files.writeString(
          directory.resolve("usage.json"), "{\"date\":\"2026-11-12\",\"requests\":1}");
      assertThrows(IllegalStateException.class, policy::beforeRequest);
      assertTrue(Files.readString(directory.resolve("usage.json")).contains("2026-11-12"));
    }
  }

  @Test
  void statusIsReadOnlyAndPolicyHasAConservativeCeiling() {
    Path absent = directory.resolve("absent");
    try (var policy = new BaoStockRequestPolicy(absent, 40000, day)) {
      assertEquals(0, policy.usage().requests());
      assertFalse(Files.exists(absent));
      assertThrows(IllegalStateException.class, policy::beforeRequest);
    }
    assertThrows(
        IllegalArgumentException.class, () -> new BaoStockRequestPolicy(directory, 50000, day));
  }
}
