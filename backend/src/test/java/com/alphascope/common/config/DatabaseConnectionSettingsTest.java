package com.alphascope.common.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 本机、可选容器及Spring覆盖的连接配置保持一致，不输出凭据。
 *
 * @author chenxuefeng
 */
class DatabaseConnectionSettingsTest {
  @Test
  void migrationCannotSilentlyUseApplicationPassword() {
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseConnectionSettings.forMigration(
                Map.of("MYSQL_PASSWORD", "application-fixture")));
    var result =
        DatabaseConnectionSettings.forMigration(
            Map.of(
                "MYSQL_PASSWORD",
                "application-fixture",
                "MYSQL_MIGRATION_PASSWORD",
                "",
                "LOCAL_MYSQL_ADMIN_PASSWORD",
                "admin-fixture",
                "LOCAL_MYSQL_ADMIN_USER",
                "root"));
    assertEquals("root", result.username());
    assertEquals("admin-fixture", result.password());
    assertFalse(result.toString().contains("admin-fixture"));
  }

  @Test
  void defaultsToNativeLocalMysqlWithoutDocker() {
    var result =
        DatabaseConnectionSettings.fromEnvironment(Map.of("MYSQL_PASSWORD", "fixture-secret"));
    assertTrue(result.url().startsWith("jdbc:mysql://127.0.0.1:3306/alphascope?"));
    assertEquals("alphascope", result.username());
    assertFalse(result.toString().contains("fixture-secret"));
  }

  @Test
  void appliesTheSameHostPortDatabaseAndAccountAsApplicationSettings() {
    var result =
        DatabaseConnectionSettings.fromEnvironment(
            Map.of(
                "MYSQL_HOST",
                "localhost",
                "MYSQL_PORT",
                "3307",
                "MYSQL_DATABASE",
                "alphascope_test",
                "MYSQL_USER",
                "research",
                "MYSQL_PASSWORD",
                "fixture-secret"));
    assertTrue(result.url().startsWith("jdbc:mysql://localhost:3307/alphascope_test?"));
    assertEquals("research", result.username());
    assertEquals("fixture-secret", result.password());
  }

  @Test
  void explicitSpringSettingsOverrideAllMysqlAliases() {
    var result =
        DatabaseConnectionSettings.fromEnvironment(
            Map.of(
                "SPRING_DATASOURCE_URL",
                "jdbc:mysql://db.internal:3308/private_db?password=url-fixture",
                "SPRING_DATASOURCE_USERNAME",
                "operator",
                "SPRING_DATASOURCE_PASSWORD",
                "spring-fixture",
                "MYSQL_PASSWORD",
                "other-fixture"));
    assertEquals("jdbc:mysql://db.internal:3308/private_db?password=url-fixture", result.url());
    assertEquals("operator", result.username());
    assertEquals("spring-fixture", result.password());
    assertFalse(result.toString().contains("url-fixture"));
    assertFalse(result.toString().contains("spring-fixture"));
  }

  @Test
  void missingOrExplicitEmptyEffectivePasswordIsRejected() {
    assertThrows(
        IllegalStateException.class, () -> DatabaseConnectionSettings.fromEnvironment(Map.of()));
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseConnectionSettings.fromEnvironment(
                Map.of("SPRING_DATASOURCE_PASSWORD", "", "MYSQL_PASSWORD", "unused")));
  }
}
