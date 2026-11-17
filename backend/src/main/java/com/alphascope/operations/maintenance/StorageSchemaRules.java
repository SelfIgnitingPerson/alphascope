package com.alphascope.operations.maintenance;

import com.alphascope.identity.config.TenantTables;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 从唯一基线安装原子预算保护，只有显式、无活动任务的结构维护可重建计量。
 *
 * @author chenxuefeng
 */
public final class StorageSchemaRules {
  private StorageSchemaRules() {}

  /**
   * 安装与基线一致的触发器并按实际正文核对台账。
   *
   * @param connection 已停止业务写入的维护连接
   * @param baseline 唯一基线
   * @return 实际DDL数
   * @throws SQLException 数据库拒绝
   */
  public static int install(Connection connection, String baseline) throws SQLException {
    var triggers = triggers(baseline);
    int changed = 0;
    try (var seed = connection.createStatement()) {
      seed.executeUpdate(
          "INSERT IGNORE INTO storage_budget(scope_id,limit_bytes) VALUES(-1,1073741824),(0,10737418240)");
    }
    Map<String, String> current = new HashMap<>();
    try (var q = connection.createStatement();
        var rows =
            q.executeQuery(
                "SELECT TRIGGER_NAME,ACTION_STATEMENT FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND TRIGGER_NAME LIKE 'as_budget_%'")) {
      while (rows.next()) current.put(rows.getString(1), rows.getString(2));
    }
    boolean rebuild =
        triggers.entrySet().stream()
                .anyMatch(
                    e ->
                        !normalize(body(e.getValue()))
                            .equals(normalize(current.getOrDefault(e.getKey(), ""))))
            || current.size() != triggers.size();
    if (!rebuild) return 0;
    for (var name : current.keySet()) {
      if (!name.matches("as_budget_[a-z_]+")) throw new IllegalStateException("预算触发器名称无效");
      try (var q = connection.createStatement()) {
        q.execute("DROP TRIGGER `" + name + "`");
        changed++;
      }
    }
    reconcile(connection, baseline);
    for (var ddl : triggers.values()) {
      try (var q = connection.createStatement()) {
        q.execute(ddl);
        changed++;
      }
    }
    return changed;
  }

  /**
   * 基线声明的完整保护触发器。
   *
   * @param baseline 唯一SQL
   * @return 名称与原始DDL
   */
  public static Map<String, String> triggers(String baseline) {
    Map<String, String> result = new LinkedHashMap<>();
    var matches =
        Pattern.compile("CREATE TRIGGER (as_budget_[a-z_]+) ([\\s\\S]*?END)\\$\\$")
            .matcher(baseline);
    while (matches.find())
      result.put(matches.group(1), matches.group().substring(0, matches.group().length() - 2));
    if (result.isEmpty()) throw new IllegalStateException("基线缺少存储预算保护");
    return result;
  }

  private static String body(String ddl) {
    return ddl.substring(ddl.indexOf("BEGIN"));
  }

  private static String normalize(String value) {
    return value.replaceAll("\\s+", " ").strip();
  }

  private static void reconcile(Connection c, String baseline) throws SQLException {
    Map<Long, Long> used = new TreeMap<>(), reserved = new TreeMap<>();
    used.put(0L, 0L);
    var tables =
        Pattern.compile("CREATE TABLE `([a-z_]+)` \\(([\\s\\S]*?)\\) ENGINE=").matcher(baseline);
    while (tables.find()) {
      String name = tables.group(1);
      var columns =
          Pattern.compile("(?m)^\\s*`([a-z_]+)` (?:json|longtext|mediumtext|text)\\b")
              .matcher(tables.group(2));
      List<String> sizes = new ArrayList<>();
      while (columns.find()) sizes.add("COALESCE(OCTET_LENGTH(`" + columns.group(1) + "`),0)");
      if (sizes.isEmpty()) continue;
      String expr = String.join("+", sizes);
      boolean own = TenantTables.PRIVATE.contains(name);
      try (var q = c.createStatement();
          var rows =
              q.executeQuery(
                  "SELECT "
                      + (own ? "tenant_id," : "0,")
                      + "COALESCE(SUM("
                      + expr
                      + "),0) FROM `"
                      + name
                      + "`"
                      + (own ? " GROUP BY tenant_id" : ""))) {
        while (rows.next()) {
          long id = rows.getLong(1), bytes = rows.getLong(2);
          used.merge(0L, bytes, Long::sum);
          if (own) used.merge(id, bytes, Long::sum);
        }
      }
    }
    for (var table : List.of("job_execution", "research_validation_task")) {
      String scope =
          table.equals("job_execution")
              ? "CASE WHEN job_name IN ('DAILY','WEEKLY') THEN tenant_id ELSE 0 END"
              : "tenant_id";
      try (var q = c.createStatement();
          var rows =
              q.executeQuery(
                  "SELECT "
                      + scope
                      + ",SUM(reservation_bytes) FROM "
                      + table
                      + " WHERE status IN ('PENDING','RUNNING','CANCEL_REQUESTED','TIMEOUT_REQUESTED') GROUP BY "
                      + scope)) {
        while (rows.next()) {
          long id = rows.getLong(1), bytes = rows.getLong(2);
          reserved.merge(0L, bytes, Long::sum);
          if (id > 0) reserved.merge(id, bytes, Long::sum);
        }
      }
    }
    Set<Long> scopes = new TreeSet<>(used.keySet());
    scopes.addAll(reserved.keySet());
    try (var q = c.createStatement()) {
      q.executeUpdate("UPDATE storage_budget SET used_bytes=0,reserved_bytes=0 WHERE scope_id>=0");
    }
    try (var q =
        c.prepareStatement(
            "INSERT INTO storage_budget(scope_id,used_bytes,reserved_bytes,limit_bytes) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE used_bytes=VALUES(used_bytes),reserved_bytes=VALUES(reserved_bytes)")) {
      for (long id : scopes) {
        q.setLong(1, id);
        q.setLong(2, used.getOrDefault(id, 0L));
        q.setLong(3, reserved.getOrDefault(id, 0L));
        q.setLong(4, id == 0 ? 10737418240L : 1073741824L);
        q.executeUpdate();
      }
    }
  }
}
