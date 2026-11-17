package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 普通应用连接验证原子计量、超额回滚、预留释放及租户外键，不修改MySQL全局权限。
 *
 * @author chenxuefeng
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class StorageBudgetIntegrationTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.40");

  @Test
  void allEvidenceWritesAreBoundedAndReservationsRollbackOrRelease() throws Exception {
    Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).load().migrate();
    try (var c =
            DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var q = c.createStatement()) {
      q.executeUpdate("UPDATE storage_budget SET limit_bytes=2000 WHERE scope_id IN(0,-1)");
      q.executeUpdate(
          "INSERT INTO research_plan(symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES('600000.SH','SHORT','TEST',REPEAT('x',600),REPEAT('a',64))");
      assertEquals(600, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=0"));
      assertEquals(600, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=1"));
      q.executeUpdate(
          "INSERT INTO job_execution(job_name,business_date,attempt,status,reservation_bytes) VALUES('DAILY','2026-11-17',1,'PENDING',700)");
      assertEquals(700, number(q, "SELECT reserved_bytes FROM storage_budget WHERE scope_id=0"));
      var failure =
          assertThrows(
              SQLException.class,
              () ->
                  q.executeUpdate(
                      "INSERT INTO research_plan(symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES('600000.SH','SHORT','TEST',REPEAT('x',800),REPEAT('b',64))"));
      assertTrue(failure.getMessage().contains("ALPHASCOPE_STORAGE_"));
      assertEquals(1, number(q, "SELECT COUNT(*) FROM research_plan"));
      assertEquals(600, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=0"));
      q.executeUpdate("UPDATE job_execution SET status='CANCELLED' WHERE job_name='DAILY'");
      assertEquals(0, number(q, "SELECT reserved_bytes FROM storage_budget WHERE scope_id=0"));
      c.setAutoCommit(false);
      q.executeUpdate(
          "INSERT INTO research_plan(symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES('600000.SH','SHORT','TEST',REPEAT('x',500),REPEAT('c',64))");
      c.rollback();
      c.setAutoCommit(true);
      assertEquals(600, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=0"));
      q.executeUpdate("UPDATE research_plan SET snapshot_json=REPEAT('x',900) WHERE id=1");
      assertEquals(900, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=0"));
      q.executeUpdate("UPDATE storage_budget SET limit_bytes=1000 WHERE scope_id=1");
      assertThrows(
          SQLException.class,
          () ->
              q.executeUpdate(
                  "INSERT INTO research_plan(symbol,horizon,action_code,snapshot_json,snapshot_hash) VALUES('600000.SH','SHORT','TEST',REPEAT('x',200),REPEAT('d',64))"));
      assertEquals(900, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=0"));
      q.executeUpdate("DELETE FROM research_plan WHERE id=1");
      assertEquals(0, number(q, "SELECT used_bytes FROM storage_budget WHERE scope_id=0"));
      q.executeUpdate(
          "INSERT INTO job_execution(job_name,business_date,attempt,status,reservation_bytes) VALUES('MARKET_HISTORY','2026-11-17',1,'PENDING',1200)");
      assertEquals(1200, number(q, "SELECT reserved_bytes FROM storage_budget WHERE scope_id=0"));
      assertEquals(0, number(q, "SELECT reserved_bytes FROM storage_budget WHERE scope_id=1"));
      q.executeUpdate("UPDATE job_execution SET status='FAILED' WHERE job_name='MARKET_HISTORY'");
      assertEquals(0, number(q, "SELECT reserved_bytes FROM storage_budget WHERE scope_id=0"));
    }
  }

  private long number(Statement q, String sql) throws SQLException {
    try (var r = q.executeQuery(sql)) {
      assertTrue(r.next());
      return r.getLong(1);
    }
  }
}
