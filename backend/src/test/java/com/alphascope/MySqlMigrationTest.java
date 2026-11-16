package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 单份开发基线：验证空库安装、重复启动，以及保留现有业务数据的显式基线登记。
 *
 * @author chenxuefeng
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class MySqlMigrationTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.40");

  @Test
  void singleBaselineInitializesAndAdoptsExistingSchemaWithoutRewritingData() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .baselineVersion("1")
            .load();
    assertEquals(1, flyway.migrate().migrationsExecuted);
    flyway.validate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
    try (var connection =
            DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var statement = connection.createStatement()) {
      for (var expected : Map.of("theme", 5, "instrument", 3, "watchlist", 2).entrySet())
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + expected.getKey())) {
          rows.next();
          assertEquals(expected.getValue(), rows.getInt(1));
        }
      try (var rows =
          statement.executeQuery(
              "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'")) {
        rows.next();
        assertEquals(41, rows.getInt(1));
      }
      // 两个来源的同日行情、同源高精度因子，以及覆盖大JSON查询的最终索引直接存在于基线。
      try (var rows =
          statement.executeQuery(
              "SELECT table_name,table_comment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'")) {
        while (rows.next())
          assertTrue(
              rows.getString("table_comment").matches(".*\\p{IsHan}.*"),
              "缺少中文表注释：" + rows.getString("table_name"));
      }
      try (var rows =
          statement.executeQuery(
              "SELECT table_name,column_name,column_comment FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'")) {
        while (rows.next())
          assertTrue(
              rows.getString("column_comment").matches(".*\\p{IsHan}.*"),
              "缺少中文字段注释：" + rows.getString("table_name") + "." + rows.getString("column_name"));
      }
      statement.executeUpdate(
          "INSERT INTO daily_bar(instrument_id,trade_date,open,high,low,close,volume,amount,source,fetched_at,batch_id,is_synthetic) SELECT id,'2026-10-30',10,11,9,10,100,1000,'BAOSTOCK','2026-10-31 16:00:00','preserved-data',0 FROM instrument WHERE symbol='300476.SZ'");
      statement.executeUpdate(
          "INSERT INTO daily_bar(instrument_id,trade_date,open,high,low,close,volume,amount,source,fetched_at,batch_id,is_synthetic) SELECT id,'2026-10-30',10,11,9,10,100,1000,'SINA','2026-10-31 16:00:00','other-source',0 FROM instrument WHERE symbol='300476.SZ'");
      statement.executeUpdate(
          "INSERT INTO adjustment_factor(instrument_id,trade_date,factor,source,is_synthetic) SELECT id,'2026-10-30',1.1234567890123456,'BAOSTOCK',0 FROM instrument WHERE symbol='300476.SZ'");
      // 仅在一次性容器中模拟已经存在的十步开发历史，验证重新登记不会执行建表或回填。
      statement.executeUpdate("DELETE FROM flyway_schema_history");
      for (int n = 1; n <= 10; n++)
        statement.executeUpdate(
            "INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,installed_by,execution_time,success) VALUES("
                + n
                + ",'"
                + n
                + "','previous development step','SQL','previous.sql','test',0,1)");
      connection.setAutoCommit(false);
      statement.executeUpdate("DROP TABLE flyway_schema_history");
      connection.commit();
    }
    flyway.baseline();
    flyway.validate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
    try (var connection =
            DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var statement = connection.createStatement()) {
      try (var rows = statement.executeQuery("SELECT type,version FROM flyway_schema_history")) {
        assertTrue(rows.next());
        assertEquals("BASELINE", rows.getString("type"));
        assertEquals("1", rows.getString("version"));
        assertFalse(rows.next());
      }
      try (var rows = statement.executeQuery("SELECT COUNT(*) FROM daily_bar")) {
        rows.next();
        assertEquals(2, rows.getInt(1));
      }
      try (var rows =
          statement.executeQuery("SELECT factor FROM adjustment_factor WHERE source='BAOSTOCK'")) {
        rows.next();
        assertEquals("1.1234567890123456", rows.getBigDecimal(1).toPlainString());
      }
      try (var rows =
          statement.executeQuery(
              "SELECT close,fetched_at FROM daily_bar WHERE batch_id='preserved-data'")) {
        rows.next();
        assertEquals("10.0000", rows.getBigDecimal("close").toPlainString());
        assertEquals(
            LocalDateTime.of(2026, 10, 31, 16, 0),
            rows.getTimestamp("fetched_at").toLocalDateTime());
      }
    }
  }
}
