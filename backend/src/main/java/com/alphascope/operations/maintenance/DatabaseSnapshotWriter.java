package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.operations.maintenance.DatabaseMaintenance.Manifest;
import com.alphascope.operations.maintenance.DatabaseMaintenance.TableSignature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import javax.sql.DataSource;

/**
 * 只读一致性导出和逐表指纹核验，结构与数据来自同一个事务快照。
 *
 * @author chenxuefeng
 */
final class DatabaseSnapshotWriter {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .findAndRegisterModules()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
  private final DataSource dataSource;
  private final BackupFiles files;
  private final Clock clock;

  DatabaseSnapshotWriter(DataSource dataSource, BackupFiles files, Clock clock) {
    this.dataSource = dataSource;
    this.files = files;
    this.clock = clock;
  }

  Manifest backup() throws Exception {
    String name =
        "alphascope-"
            + DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                .withZone(ZoneId.of("Asia/Shanghai"))
                .format(clock.instant())
            + "-"
            + UUID.randomUUID().toString().substring(0, 8)
            + ".sql.gz";
    Path partial = files.directory.resolve(name + ".partial");
    files.privateFile(partial);
    Map<String, TableSignature> signatures = new LinkedHashMap<>();
    try (var connection = dataSource.getConnection();
        var zone = new BackupSession(connection);
        var writer =
            new BufferedWriter(
                new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(partial)),
                    StandardCharsets.UTF_8))) {
      requireProject(connection);
      connection.setNetworkTimeout(Runnable::run, 60000);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      execute(connection, "START TRANSACTION WITH CONSISTENT SNAPSHOT");
      writer.write(
          "SET NAMES utf8mb4;\nSET time_zone='+00:00';\nSET FOREIGN_KEY_CHECKS=0;\nSET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n");
      for (String table : tables(connection)) {
        String create = createTable(connection, table);
        writer.write(create);
        writer.write(";\n");
        signatures.put(table, table(connection, table, null, writer));
      }
      writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
      connection.commit();
    } catch (Exception e) {
      Files.deleteIfExists(partial);
      throw e;
    }
    Path complete = files.directory.resolve(name);
    Files.move(partial, complete);
    Manifest manifest =
        new Manifest(1, name, files.hashFile(complete), clock.instant(), signatures);
    files.writePrivate(files.manifestPath(name), Jsons.write(manifest));
    return manifest;
  }

  Manifest capture(Connection connection, Instant observedAt) throws Exception {
    Map<String, TableSignature> values = new LinkedHashMap<>();
    for (String name : tables(connection)) {
      values.put(name, table(connection, name, null, null));
    }
    return new Manifest(1, null, null, observedAt, values);
  }

  void assertSignatures(Connection connection, Manifest manifest, boolean structure)
      throws Exception {
    if (structure
        ? !tables(connection).equals(new ArrayList<>(manifest.tables().keySet()))
        : !tables(connection).containsAll(manifest.tables().keySet())) {
      throw new IllegalStateException("恢复后的表集合不一致");
    }
    if (!structure) {
      var additional = new HashSet<>(tables(connection));
      additional.removeAll(manifest.tables().keySet());
      if (!Set.of(
              "signal_revision",
              "validation_experiment",
              "research_validation_task",
              "app_user",
              "auth_session",
              "auth_event",
              "source_license",
              "service_alert")
          .containsAll(additional)) {
        throw new IllegalStateException("出现未授权的新表");
      }
    }
    for (var entry : manifest.tables().entrySet()) {
      TableSignature actual = table(connection, entry.getKey(), entry.getValue().columns(), null);
      TableSignature expected = entry.getValue();
      if (actual.rows() != expected.rows()
          || !actual.contentHash().equals(expected.contentHash())
          || structure && !actual.structureHash().equals(expected.structureHash())) {
        throw new IllegalStateException("原有表结构或数据核对失败：" + entry.getKey());
      }
    }
  }

  TableSignature table(
      Connection connection, String name, List<String> selected, BufferedWriter writer)
      throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    List<String> columns = selected == null ? columns(connection, name) : selected;
    String names =
        String.join(",", columns.stream().map(DatabaseSnapshotWriter::identifier).toList());
    List<String> keys = new ArrayList<>();
    try (var result =
        connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, name)) {
      var ordered = new TreeMap<Integer, String>();
      while (result.next()) {
        ordered.put(result.getInt("KEY_SEQ"), result.getString("COLUMN_NAME"));
      }
      keys.addAll(ordered.values());
    }
    if (keys.isEmpty()) {
      throw new IllegalStateException("备份表必须有稳定主键：" + name);
    }
    String sql =
        "SELECT "
            + names
            + " FROM "
            + identifier(name)
            + " ORDER BY "
            + String.join(",", keys.stream().map(DatabaseSnapshotWriter::identifier).toList());
    long count = 0;
    try (var statement = connection.createStatement()) {
      statement.setQueryTimeout(120);
      statement.setFetchSize(Integer.MIN_VALUE);
      try (var rows = statement.executeQuery(sql)) {
        while (rows.next()) {
          count++;
          if (writer != null) {
            writer.write("INSERT INTO " + identifier(name) + " (" + names + ") VALUES (");
          }
          for (int index = 1; index <= columns.size(); index++) {
            String value = rows.getString(index);
            String canonical = value;
            String type = rows.getMetaData().getColumnTypeName(index);
            if (value != null && "JSON".equalsIgnoreCase(type)) {
              canonical = canonicalJson(JSON.readTree(value)).toString();
            }
            if (value != null
                && List.of("DECIMAL", "NUMERIC", "DOUBLE", "FLOAT")
                    .contains(type.toUpperCase(Locale.ROOT))) {
              canonical = new BigDecimal(value).stripTrailingZeros().toPlainString();
            }
            byte[] bytes =
                canonical == null ? new byte[0] : canonical.getBytes(StandardCharsets.UTF_8);
            digest.update(
                (canonical == null ? "NULL:" : "VALUE:" + bytes.length + ":")
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(bytes);
            if (writer != null) {
              if (index > 1) {
                writer.write(',');
              }
              writer.write(
                  value == null
                      ? "NULL"
                      : "CONVERT(X'"
                          + HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8))
                          + "' USING utf8mb4)");
            }
          }
          digest.update((byte) '\n');
          if (writer != null) {
            writer.write(");\n");
          }
        }
      }
    }
    return new TableSignature(
        columns,
        count,
        Jsons.hash(
            createTable(connection, name).replaceAll("AUTO_INCREMENT=\\d+", "AUTO_INCREMENT=?")),
        HexFormat.of().formatHex(digest.digest()));
  }

  JsonNode canonicalJson(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = JSON.createObjectNode();
      var names = new TreeSet<String>();
      node.fieldNames().forEachRemaining(names::add);
      names.forEach(name -> result.set(name, canonicalJson(node.get(name))));
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = JSON.createArrayNode();
      node.forEach(value -> result.add(canonicalJson(value)));
      return result;
    }
    if (node.isNumber()) {
      return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
    }
    return node;
  }

  List<String> tables(Connection connection) throws SQLException {
    List<String> result = new ArrayList<>();
    try (var rows =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
      while (rows.next()) {
        result.add(rows.getString("TABLE_NAME"));
      }
    }
    result.sort(String::compareTo);
    return result;
  }

  List<String> columns(Connection connection, String table) throws SQLException {
    List<String> result = new ArrayList<>();
    try (var rows =
        connection.getMetaData().getColumns(connection.getCatalog(), null, table, "%")) {
      var ordered = new TreeMap<Integer, String>();
      while (rows.next()) {
        ordered.put(rows.getInt("ORDINAL_POSITION"), rows.getString("COLUMN_NAME"));
      }
      result.addAll(ordered.values());
    }
    return result;
  }

  String createTable(Connection connection, String table) throws SQLException {
    try (var statement = connection.createStatement();
        var row = statement.executeQuery("SHOW CREATE TABLE " + identifier(table))) {
      row.next();
      return row.getString(2);
    }
  }

  static String identifier(String name) {
    if (!name.matches("[a-zA-Z0-9_]+")) {
      throw new IllegalArgumentException("不支持的数据库标识符");
    }
    return "`" + name + "`";
  }

  void requireProject(Connection connection) throws SQLException {
    if (!"alphascope".equals(connection.getCatalog())) {
      throw new IllegalStateException("维护工具只允许项目alphascope数据库");
    }
  }

  void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
