package com.alphascope.operations;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.operations.maintenance.DatabaseMaintenance;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.zip.GZIPInputStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 实际压缩导出、隔离Docker恢复和开发基线同步的回归，验证精确数值、JSON、空值及时间语义。
 *
 * @author chenxuefeng
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class DatabaseMaintenanceTest {
  @Container
  private static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.0.40")
          .withDatabaseName("alphascope")
          .withUsername("alphascope")
          .withPassword("isolated-test-only");

  @TempDir Path root;

  @Test
  void backupReallyRestoresAndSchemaSyncRetainsAllOriginalData() throws Exception {
    var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      // 仅此测试构造旧库：先移除新预算触发器，再模拟不存在的新字段。
      var guards = new java.util.ArrayList<String>();
      try (var rows =
          statement.executeQuery(
              "SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name LIKE 'as_budget_%'")) {
        while (rows.next()) guards.add(rows.getString(1));
      }
      for (String guard : guards) statement.execute("DROP TRIGGER `" + guard + "`");
      statement.execute("DROP TABLE flyway_schema_history");
      statement.execute("DROP TABLE validation_experiment");
      statement.execute("DROP TABLE research_validation_task");
      statement.execute("DROP TABLE signal_revision");
      statement.execute("ALTER TABLE market_data_revision DROP COLUMN recorded_at");
      statement.execute("ALTER TABLE job_execution DROP COLUMN data_status");
      statement.execute("ALTER TABLE job_stage DROP COLUMN data_status, DROP COLUMN details_json");
      statement.execute(
          "ALTER TABLE job_execution MODIFY COLUMN status varchar(32) NOT NULL COMMENT '旧执行状态'");
      statement.execute(
          "ALTER TABLE job_stage MODIFY COLUMN status varchar(32) NOT NULL COMMENT '旧阶段状态'");
      statement.execute(
          "CREATE TABLE backup_probe (id bigint PRIMARY KEY, value_decimal decimal(32,10), value_json json, value_text longtext, value_null varchar(32), value_time timestamp(6) NULL) ENGINE=InnoDB");
      try (var insert =
          connection.prepareStatement(
              "INSERT INTO backup_probe VALUES (1, ?, ?, ?, NULL, '2026-10-30 07:12:13.123456')")) {
        insert.setBigDecimal(1, new BigDecimal("12345678901234567890.1234567890"));
        insert.setString(2, "{\"n\":12345678901234567890.123456789,\"z\":[null,0.1000,\"中文\"]}");
        insert.setString(3, "中文、引号'、反斜杠\\、换行\n、空字符\u0000");
        insert.executeUpdate();
      }
    }
    Flyway.configure().dataSource(source).baselineVersion("1").load().baseline();
    Path baseline = root.resolve("backend/src/main/resources/db/migration/V1__baseline.sql");
    Files.createDirectories(baseline.getParent());
    try (var input = getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
      Files.copy(input, baseline);
    }
    var maintenance = new DatabaseMaintenance(source, root, Clock.systemUTC());
    var result = maintenance.backupAndVerify(14);
    assertTrue(result.verified());
    assertEquals(45, result.tables());
    try (var input =
        new GZIPInputStream(
            Files.newInputStream(root.resolve(".runtime/backups").resolve(result.fileName())))) {
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(sql.contains("SET time_zone='+00:00'"));
      assertFalse(sql.contains(MYSQL.getPassword()));
    }
    Path second = root.resolve("second-store");
    Files.createDirectories(second);
    var replica = maintenance.replicate(result, second, 14);
    assertEquals("VERIFIED_SAME_FILESYSTEM", replica.status());
    assertFalse(replica.differentFileSystem());
    assertEquals(
        result.sha256(),
        com.alphascope.common.util.Jsons.hashFile(
            second.resolve("alphascope-backups").resolve(result.fileName())));
    Files.writeString(
        second.resolve("alphascope-backups").resolve(result.fileName()), "tampered replica");
    assertThrows(IllegalStateException.class, () -> maintenance.replicate(result, second, 14));
    assertEquals(
        result.sha256(),
        com.alphascope.common.util.Jsons.hashFile(
            root.resolve(".runtime/backups").resolve(result.fileName())));
    assertTrue(maintenance.upgradeSchema() >= 9);
    assertEquals(0, maintenance.upgradeSchema(), "重复执行不能继续产生DDL");
    Path direct = root.resolve("direct-no-backup");
    Path directBaseline =
        direct.resolve("backend/src/main/resources/db/migration/V1__baseline.sql");
    Files.createDirectories(directBaseline.getParent());
    Files.copy(baseline, directBaseline);
    assertEquals(
        0, new DatabaseMaintenance(source, direct, Clock.systemUTC()).upgradeSchemaDirect());
    try (var paths = Files.list(direct.resolve(".runtime/backups"))) {
      assertTrue(paths.noneMatch(path -> path.toString().endsWith(".sql.gz")));
    }
    try (var connection = source.getConnection();
        var statement = connection.createStatement();
        var row =
            statement.executeQuery(
                "SELECT value_decimal,value_text,value_null FROM backup_probe")) {
      assertTrue(row.next());
      assertEquals("12345678901234567890.1234567890", row.getBigDecimal(1).toPlainString());
      assertTrue(row.getString(2).contains("\u0000"));
      assertNull(row.getString(3));
    }
  }
}
