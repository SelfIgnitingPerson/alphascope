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
      for (String[] column :
          List.of(
              new String[] {"job_execution", "data_status"},
              new String[] {"job_stage", "data_status"},
              new String[] {"job_stage", "details_json"},
              new String[] {"market_data_revision", "recorded_at"})) {
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
      for (String table : List.of("job_execution", "job_stage")) {
        String declaration = baselineColumn(baseline, table, "status");
        String comment =
            declaration.substring(declaration.indexOf("COMMENT '") + 9, declaration.length() - 1);
        try (var column =
            connection.getMetaData().getColumns(connection.getCatalog(), null, table, "status")) {
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
            writer.execute(connection, baseline.substring(start, end));
            changed++;
          }
        }
      }
      changed += isolatePrivateTables(connection, baseline);
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
