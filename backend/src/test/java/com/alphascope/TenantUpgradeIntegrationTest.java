package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.operations.maintenance.DatabaseMaintenance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 从当前唯一SQL在临时目录重建旧的单人结构，验证真实租户字段、唯一键和外键升级。
 *
 * @author chenxuefeng
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class TenantUpgradeIntegrationTest {
  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.0.40").withDatabaseName("alphascope");

  @TempDir Path root;

  @Test
  void adoptingSingleUserDataPreservesAllValuesAndAllowsIndependentNaturalKeys() throws Exception {
    String current;
    try (var input = getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
      current =
          new String(
              java.util.Objects.requireNonNull(input).readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
    }
    String legacy = current.replaceAll("(?s)-- BEGIN STORAGE GUARDS.*?-- END STORAGE GUARDS", "");
    for (String table :
        List.of(
            "storage_budget",
            "job_attempt_history",
            "corporate_action",
            "investment_thesis",
            "research_plan_review",
            "research_plan",
            "theme_leader_revision",
            "market_universe_snapshot",
            "auth_session",
            "auth_event",
            "source_license",
            "service_alert",
            "app_user"))
      legacy = legacy.replaceAll("(?s)CREATE TABLE `" + table + "` \\(.*?\\) ENGINE=.*?;", "");
    legacy =
        legacy
            .replaceAll("(?m)^INSERT INTO app_user[^;]*;", "")
            .replaceAll("(?m)^INSERT INTO storage_budget[^;]*;", "");
    legacy =
        legacy
            .replace("PRIMARY KEY (`tenant_id`,`id`)", "PRIMARY KEY (`id`)")
            .replaceAll("(?m)^\\s*`tenant_id`[^\\n]*\\n", "")
            .replaceAll("(?m)^\\s*KEY `idx_tenant_scope`[^\\n]*\\n", "")
            .replaceAll("(?m)^\\s*KEY `idx_fk_scope_[^\\n]*\\n", "")
            .replace("`tenant_id`,", "");
    Path migrations = root.resolve("temporary-legacy");
    Files.createDirectories(migrations);
    Files.writeString(migrations.resolve("V1__baseline.sql"), legacy);
    var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
    Flyway.configure().dataSource(source).locations("filesystem:" + migrations).load().migrate();
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO portfolio_account(id,total_assets,as_of_date) VALUES(1,1000000,'2026-10-30')");
      statement.execute(
          "INSERT INTO job_execution(job_name,business_date,attempt,status) VALUES('DAILY','2026-10-30',1,'SUCCESS')");
      statement.execute(
          "INSERT INTO analysis_report(report_type,business_date,revision,status,content_md,data_as_of) VALUES('DAILY','2026-10-30',1,'SUCCESS','original-report','2026-10-30 15:00:00')");
      statement.execute("DROP TABLE flyway_schema_history");
    }
    Flyway.configure().dataSource(source).baselineVersion("1").load().baseline();
    Path baseline = root.resolve("backend/src/main/resources/db/migration/V1__baseline.sql");
    Files.createDirectories(baseline.getParent());
    Files.writeString(baseline, current);
    var maintenance = new DatabaseMaintenance(source, root, Clock.systemUTC());
    assertTrue(maintenance.upgradeSchemaDirect() > 30);
    assertEquals(0, maintenance.upgradeSchemaDirect());
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      try (var rows = statement.executeQuery("SELECT tenant_id,content_md FROM analysis_report")) {
        assertTrue(rows.next());
        assertEquals(1, rows.getLong(1));
        assertEquals("original-report", rows.getString(2));
      }
      statement.execute(
          "INSERT INTO app_user(id,username,role,status) VALUES(2,'isolated-second','USER','PENDING')");
      statement.execute(
          "INSERT INTO research_plan(tenant_id,symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES(1,'600000.SH','SHORT','TEST','{}',REPEAT('a',64))");
      assertThrows(
          java.sql.SQLException.class,
          () ->
              statement.execute(
                  "INSERT INTO research_plan_review(tenant_id,plan_id,review_json,review_hash) VALUES(2,1,'{}',REPEAT('b',64))"));
      statement.execute(
          "INSERT INTO watchlist(tenant_id,instrument_id) SELECT 2,id FROM instrument WHERE symbol='300476.SZ'");
      statement.execute(
          "INSERT INTO portfolio_account(tenant_id,id,total_assets,as_of_date) VALUES(2,1,2000000,'2026-10-30')");
      statement.execute(
          "INSERT INTO job_execution(tenant_id,job_name,business_date,attempt,status) VALUES(2,'DAILY','2026-10-30',1,'SUCCESS')");
      statement.execute(
          "INSERT INTO analysis_report(tenant_id,report_type,business_date,revision,status,content_md,data_as_of) VALUES(2,'DAILY','2026-10-30',1,'SUCCESS','second-report','2026-10-30 15:00:00')");
      try (var rows =
          statement.executeQuery("SELECT COUNT(DISTINCT tenant_id) FROM portfolio_account")) {
        rows.next();
        assertEquals(2, rows.getInt(1));
      }
    }
    try (var files = Files.walk(root.resolve(".runtime"))) {
      assertTrue(files.noneMatch(path -> path.toString().endsWith(".sql.gz")));
    }
  }
}
