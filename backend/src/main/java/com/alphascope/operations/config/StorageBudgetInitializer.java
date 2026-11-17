package com.alphascope.operations.config;

import com.alphascope.operations.maintenance.StorageSchemaRules;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 工作线程启动前核对完整数据库保护并应用预算配置；计量重建只由显式维护执行。
 *
 * @author chenxuefeng
 */
@Component
@DependsOnDatabaseInitialization
public class StorageBudgetInitializer implements SmartInitializingSingleton {
  private final JdbcTemplate jdbc;
  private final ProductProperties options;

  public StorageBudgetInitializer(JdbcTemplate jdbc, ProductProperties options) {
    this.jdbc = jdbc;
    this.options = options;
  }

  @Override
  public void afterSingletonsInstantiated() {
    try (var in = getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
      var expected =
          StorageSchemaRules.triggers(
              new String(
                  java.util.Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
      var actual =
          jdbc.queryForList(
              "SELECT TRIGGER_NAME FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND TRIGGER_NAME LIKE 'as_budget_%'",
              String.class);
      if (!new java.util.HashSet<>(actual).equals(expected.keySet()))
        throw new IllegalStateException("数据库预算保护不完整，请先执行受控结构升级");
      jdbc.update(
          "UPDATE storage_budget SET limit_bytes=? WHERE scope_id=0",
          options.getMaximumPlatformEvidenceBytes());
      jdbc.update(
          "UPDATE storage_budget SET limit_bytes=? WHERE scope_id<>0",
          options.getMaximumEvidenceBytes());
    } catch (java.io.IOException e) {
      throw new IllegalStateException("无法读取预算基线", e);
    }
  }
}
