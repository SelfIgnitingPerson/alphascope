package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.TenantTables;
import com.alphascope.operations.maintenance.DatabaseMaintenance.Manifest;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * 已有开发库的受控增量结构同步：先核验新鲜恢复备份，再比对原字段内容。
 *
 * @author chenxuefeng
 */
final class DevelopmentSchemaSynchronizer {
  private final DataSource dataSource;
  private final BackupFiles files;
  private final DatabaseSnapshotWriter writer;
  private final Clock clock;

  DevelopmentSchemaSynchronizer(
      DataSource dataSource, BackupFiles files, DatabaseSnapshotWriter writer, Clock clock) {
    this.dataSource = dataSource;
    this.files = files;
    this.writer = writer;
    this.clock = clock;
  }

  int synchronize(boolean requireVerifiedBackup) throws Exception {
    files.prepare();
    try (var channel =
            FileChannel.open(
                files.root.resolve(".runtime/database-maintenance.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = channel.tryLock();
        var connection = dataSource.getConnection();
        var zone = new BackupSession(connection)) {
      if (lock == null) {
        throw new IllegalStateException("已有数据库维护在进行");
      }
      writer.requireProject(connection);
      connection.setNetworkTimeout(Runnable::run, 60000);
      try (var statement = connection.createStatement();
          var row =
              statement.executeQuery(
                  "SELECT COUNT(*) FROM job_execution WHERE status IN ('PENDING','RUNNING','CANCEL_REQUESTED','TIMEOUT_REQUESTED')")) {
        row.next();
        if (row.getInt(1) != 0) {
          throw new IllegalStateException("存在活动任务，不能同步数据库结构");
        }
      }
      try (var tables =
          connection
              .getMetaData()
              .getTables(
                  connection.getCatalog(),
                  null,
                  "research_validation_task",
                  new String[] {"TABLE"})) {
        if (tables.next()) {
          try (var statement = connection.createStatement();
              var rows =
                  statement.executeQuery(
                      "SELECT COUNT(*) FROM research_validation_task WHERE status IN ('PENDING','RUNNING','CANCEL_REQUESTED','TIMEOUT_REQUESTED')")) {
            rows.next();
            if (rows.getInt(1) != 0) throw new IllegalStateException("存在活动研究验证，不能同步数据库结构");
          }
        }
      }
      try (var statement = connection.createStatement();
          var row =
              statement.executeQuery(
                  "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='BASELINE' AND success=1")) {
        row.next();
        if (row.getInt(1) != 1) {
          throw new IllegalStateException("当前库不是已显式登记的开发基线；不能自动修复迁移历史");
        }
      }
      Manifest before =
          requireVerifiedBackup ? files.latest(true) : writer.capture(connection, clock.instant());
      if (requireVerifiedBackup
          && before.createdAt().isBefore(clock.instant().minusSeconds(3600))) {
        throw new IllegalStateException("结构同步需要一小时内已验证的备份");
      }
      writer.assertSignatures(connection, before, false);
      String baseline =
          Files.readString(
              files.root.resolve("backend/src/main/resources/db/migration/V1__baseline.sql"));
      int changed = 0;
      // 删除仅获准清理且已确认空的估值预留表；不允许借清理丢弃任何记录。
      var preserved = new LinkedHashMap<>(before.tables());
      preserved.remove("storage_budget");
      if (preserved.containsKey("valuation_snapshot")) {
        if (preserved.get("valuation_snapshot").rows() != 0)
          throw new IllegalStateException("估值预留表已有数据，拒绝自动删除");
        try (var ddl = connection.createStatement()) {
          ddl.execute("DROP TABLE valuation_snapshot");
        }
        preserved.remove("valuation_snapshot");
        changed++;
      }
      before =
          new Manifest(
              before.formatVersion(),
              before.fileName(),
              before.sha256(),
              before.createdAt(),
              preserved);
      for (String[] column :
          List.of(
              new String[] {"instrument", "delisted_at"},
              new String[] {"instrument", "lifecycle_source"},
              new String[] {"instrument", "lifecycle_observed_at"},
              new String[] {"instrument", "lifecycle_evidence_json"},
              new String[] {"instrument", "lifecycle_hash"},
              new String[] {"job_execution", "reservation_bytes"},
              new String[] {"research_validation_task", "reservation_bytes"},
              new String[] {"job_execution", "data_status"},
              new String[] {"job_stage", "data_status"},
              new String[] {"job_stage", "details_json"},
              new String[] {"market_data_revision", "recorded_at"},
              new String[] {"research_plan", "research_date"},
              new String[] {"research_plan", "acknowledged_review_hash"},
              new String[] {"research_plan_review", "review_date"},
              new String[] {"portfolio_account", "risk_budget"},
              new String[] {"portfolio_account", "cash_balance"},
              new String[] {"portfolio_account", "positions_complete"},
              new String[] {"portfolio_position", "risk_group"},
              new String[] {"financial_report", "raw_payload"},
              new String[] {"portfolio_account", "liquidity_reserve"},
              new String[] {"portfolio_account", "funds_needed_on"},
              new String[] {"portfolio_account", "funds_purpose"})) {
        try (var present =
            connection
                .getMetaData()
                .getTables(connection.getCatalog(), null, column[0], new String[] {"TABLE"})) {
          if (!present.next()) continue; // 旧库尚无整表，后续按基线建表，不能先ALTER。
        }
        try (var rows =
            connection
                .getMetaData()
                .getColumns(connection.getCatalog(), null, column[0], column[1])) {
          if (!rows.next()) {
            writer.execute(
                connection,
                "ALTER TABLE `"
                    + column[0]
                    + "` ADD COLUMN "
                    + baselineColumn(baseline, column[0], column[1]));
            changed++;
          }
        }
      }
      for (String[] target :
          List.of(
              new String[] {"job_execution", "status"},
              new String[] {"job_stage", "status"},
              new String[] {"job_execution", "job_name"})) {
        String table = target[0];
        String columnName = target[1];
        String declaration = baselineColumn(baseline, table, columnName);
        String comment =
            declaration.substring(declaration.indexOf("COMMENT '") + 9, declaration.length() - 1);
        try (var column =
            connection.getMetaData().getColumns(connection.getCatalog(), null, table, columnName)) {
          column.next();
          if (!comment.equals(column.getString("REMARKS"))) {
            writer.execute(connection, "ALTER TABLE `" + table + "` MODIFY COLUMN " + declaration);
            changed++;
          }
        }
      }
      for (String table :
          List.of(
              "signal_revision",
              "validation_experiment",
              "research_validation_task",
              "app_user",
              "auth_session",
              "auth_event",
              "source_license",
              "storage_budget",
              "theme_leader_revision",
              "market_universe_snapshot",
              "corporate_action",
              "investment_thesis",
              "portfolio_revision",
              "research_observation",
              "forward_result",
              "research_plan",
              "research_plan_review",
              "job_attempt_history",
              "service_alert")) {
        try (var rows =
            connection
                .getMetaData()
                .getTables(connection.getCatalog(), null, table, new String[] {"TABLE"})) {
          if (!rows.next()) {
            int start = baseline.indexOf("CREATE TABLE `" + table + "`");
            int end = baseline.indexOf(";", start);
            if (start < 0 || end < 0) {
              throw new IllegalStateException("当前基线缺少受控表定义");
            }
            String declaration = baseline.substring(start, end);
            // 新执行历史的父表复合键由后续统一对齐；旧库先建表，随后安装外键。
            if (table.equals("job_attempt_history"))
              declaration =
                  declaration.replaceAll(",\\s*CONSTRAINT `fk_job_attempt_parent`[^\\n]*", "");
            writer.execute(connection, declaration);
            changed++;
          }
        }
      }
      changed += isolatePrivateTables(connection, baseline);
      for (var declaration :
          List.of(
              new String[] {
                "research_plan",
                "uk_plan_evidence",
                "UNIQUE KEY uk_plan_evidence(tenant_id,symbol,horizon,action_code,snapshot_hash)"
              },
              new String[] {
                "research_plan",
                "idx_plan_date",
                "KEY idx_plan_date(tenant_id,archived,research_date)"
              },
              new String[] {
                "research_plan_review",
                "uk_review_evidence",
                "UNIQUE KEY uk_review_evidence(tenant_id,plan_id,review_hash)"
              },
              new String[] {
                "research_plan_review",
                "idx_review_date",
                "KEY idx_review_date(tenant_id,plan_id,review_date,id)"
              })) {
        boolean exists = false;
        try (var indexes =
            connection
                .getMetaData()
                .getIndexInfo(connection.getCatalog(), null, declaration[0], false, false)) {
          while (indexes.next())
            if (declaration[1].equals(indexes.getString("INDEX_NAME"))) exists = true;
        }
        if (!exists) {
          writer.execute(connection, "ALTER TABLE " + declaration[0] + " ADD " + declaration[2]);
          changed++;
        }
      }

      changed += alignTenantForeignKeys(connection);
      try (var indexes =
          connection
              .getMetaData()
              .getIndexInfo(connection.getCatalog(), null, "market_data_revision", false, false)) {
        boolean exists = false;
        while (indexes.next())
          if ("idx_revision_batch_source".equals(indexes.getString("INDEX_NAME"))) exists = true;
        if (!exists) {
          writer.execute(
              connection,
              "ALTER TABLE market_data_revision ADD KEY idx_revision_batch_source(batch_id,source)");
          changed++;
        }
      }
      changed += StorageSchemaRules.install(connection, baseline);
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery("SELECT COUNT(*) FROM app_user WHERE id=1")) {
        rows.next();
        if (rows.getInt(1) == 0)
          writer.execute(
              connection,
              "INSERT INTO app_user(id,username,role,status,created_at) VALUES(1,'local-owner','ADMIN','PENDING','"
                  + java.time.LocalDateTime.ofInstant(
                          clock.instant(), java.time.ZoneId.of("Asia/Shanghai"))
                      .format(
                          java.time.format.DateTimeFormatter.ofPattern(
                              "yyyy-MM-dd HH:mm:ss.SSSSSS"))
                  + "')");
      }
      writer.assertSignatures(connection, before, false);
      files.writePrivate(
          files.directory.resolve("latest-schema-sync.json"),
          Jsons.write(
              Map.of(
                  "verifiedBackup",
                  before.fileName() == null
                      ? "NOT_CREATED_EXPLICIT_USER_REQUEST"
                      : before.fileName(),
                  "verificationMode",
                  requireVerifiedBackup ? "RESTORE_VERIFIED_BACKUP" : "LIVE_DATA_FINGERPRINTS_ONLY",
                  "backupSha256",
                  before.sha256() == null ? "NOT_CREATED" : before.sha256(),
                  "completedAt",
                  clock.instant(),
                  "ddlCount",
                  changed,
                  "originalDataUnchanged",
                  true)));
      return changed;
    }
  }

  /** 仅将当前明确列出的私人表扩展为用户范围，原有列值不更新或重新分配。 */
  private int isolatePrivateTables(Connection connection, String baseline) throws Exception {
    int changed = 0;
    for (String table : TenantTables.PRIVATE.stream().sorted().toList()) {
      try (var columns =
          connection.getMetaData().getColumns(connection.getCatalog(), null, table, "tenant_id")) {
        if (!columns.next()) {
          writer.execute(
              connection,
              "ALTER TABLE `"
                  + table
                  + "` ADD COLUMN "
                  + baselineColumn(baseline, table, "tenant_id"));
          changed++;
        }
      }
      String declaration = tableDeclaration(baseline, table);
      var target = new LinkedHashMap<String, List<String>>();
      var pattern =
          Pattern.compile("(?m)^\\s*(PRIMARY KEY|(?:UNIQUE )?KEY `([^`]+)`) \\(([^)]+)\\)");
      var matcher = pattern.matcher(declaration);
      while (matcher.find()) {
        String name = matcher.group(1).equals("PRIMARY KEY") ? "PRIMARY" : matcher.group(2);
        var names =
            Arrays.stream(matcher.group(3).replace("`", "").split(",")).map(String::trim).toList();
        if (names.contains("tenant_id") || name.startsWith("idx_fk_scope_"))
          target.put(name, names);
      }
      // 先补外键支撑索引，随后才替换旧的全局唯一键。
      var order =
          target.keySet().stream()
              .sorted(Comparator.comparingInt(name -> name.startsWith("idx_fk_scope_") ? 0 : 1))
              .toList();
      for (String name : order) {
        List<String> existing = new ArrayList<>();
        if (name.equals("PRIMARY"))
          try (var keys =
              connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, table)) {
            var values = new TreeMap<Short, String>();
            while (keys.next()) values.put(keys.getShort("KEY_SEQ"), keys.getString("COLUMN_NAME"));
            existing.addAll(values.values());
          }
        else
          try (var keys =
              connection
                  .getMetaData()
                  .getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            var values = new TreeMap<Short, String>();
            while (keys.next())
              if (name.equals(keys.getString("INDEX_NAME")))
                values.put(keys.getShort("ORDINAL_POSITION"), keys.getString("COLUMN_NAME"));
            existing.addAll(values.values());
          }
        if (existing.equals(target.get(name))) continue;
        String columns =
            String.join(",", target.get(name).stream().map(value -> "`" + value + "`").toList());
        String prefix =
            name.equals("PRIMARY")
                ? "PRIMARY KEY"
                : (declaration.contains("UNIQUE KEY `" + name + "`")
                    ? "UNIQUE KEY `" + name + "`"
                    : "KEY `" + name + "`");
        String drop =
            existing.isEmpty()
                ? ""
                : name.equals("PRIMARY") ? "DROP PRIMARY KEY, " : "DROP INDEX `" + name + "`, ";
        writer.execute(
            connection,
            "ALTER TABLE `" + table + "` " + drop + "ADD " + prefix + " (" + columns + ")");
        changed++;
      }
    }
    return changed;
  }

  /** 对同一私人空间的父子关系补数据库约束，先拒绝现有错配。 */
  private int alignTenantForeignKeys(Connection c) throws Exception {
    int changed = 0;
    for (var relation :
        List.of(
            new String[] {"job_stage", "job_execution", "job_id", "job_stage_ibfk_1"},
            new String[] {
              "job_attempt_history", "job_execution", "job_id", "fk_job_attempt_parent"
            },
            new String[] {
              "report_input_snapshot",
              "analysis_report",
              "report_id",
              "report_input_snapshot_ibfk_1"
            },
            new String[] {"report_source", "analysis_report", "report_id", "report_source_ibfk_1"},
            new String[] {
              "notification_log", "analysis_report", "report_id", "notification_log_ibfk_1"
            })) {
      boolean tenant = false, exists = false;
      try (var keys = c.getMetaData().getImportedKeys(c.getCatalog(), null, relation[0])) {
        while (keys.next())
          if (relation[3].equals(keys.getString("FK_NAME"))) {
            exists = true;
            if ("tenant_id".equals(keys.getString("FKCOLUMN_NAME"))) tenant = true;
          }
      }
      if (tenant) continue;
      try (var q = c.createStatement();
          var rows =
              q.executeQuery(
                  "SELECT COUNT(*) FROM "
                      + relation[0]
                      + " x JOIN "
                      + relation[1]
                      + " p ON p.id=x."
                      + relation[2]
                      + " WHERE p.tenant_id<>x.tenant_id")) {
        rows.next();
        if (rows.getLong(1) > 0) throw new IllegalStateException("发现父子记录租户不一致，拒绝升级约束");
      }
      if (exists) {
        writer.execute(c, "ALTER TABLE " + relation[0] + " DROP FOREIGN KEY " + relation[3]);
        changed++;
      }
      try {
        writer.execute(
            c,
            "ALTER TABLE "
                + relation[0]
                + " ADD CONSTRAINT "
                + relation[3]
                + " FOREIGN KEY(tenant_id,"
                + relation[2]
                + ") REFERENCES "
                + relation[1]
                + "(tenant_id,id)");
        changed++;
      } catch (Exception failed) {
        if (exists)
          writer.execute(
              c,
              "ALTER TABLE "
                  + relation[0]
                  + " ADD CONSTRAINT "
                  + relation[3]
                  + " FOREIGN KEY("
                  + relation[2]
                  + ") REFERENCES "
                  + relation[1]
                  + "(id)");
        throw failed;
      }
    }
    return changed;
  }

  private String tableDeclaration(String sql, String table) {
    int start = sql.indexOf("CREATE TABLE `" + table + "`");
    int end = sql.indexOf(") ENGINE=", start);
    if (start < 0 || end < 0) throw new IllegalStateException("缺少受控表定义");
    return sql.substring(start, end);
  }

  private String baselineColumn(String sql, String table, String column) {
    int start = sql.indexOf("CREATE TABLE `" + table + "`");
    int end = sql.indexOf(") ENGINE=", start);
    return sql.substring(start, end)
        .lines()
        .map(String::strip)
        .filter(line -> line.startsWith("`" + column + "` "))
        .findFirst()
        .orElseThrow()
        .replaceAll(",$", "");
  }
}
