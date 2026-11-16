package com.alphascope.operations.maintenance;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 工具连接的TIMESTAMP规范化会话，关闭恢复原时区，不污染连接池。
 *
 * @author chenxuefeng
 */
final class BackupSession implements AutoCloseable {
  /** 当前工具连接。 */
  private final Connection connection;

  /** 该池连接原有的会话时区。 */
  private final String previous;

  BackupSession(Connection connection) throws SQLException {
    this.connection = connection;
    try (var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT @@session.time_zone")) {
      result.next();
      previous = result.getString(1);
    }
    set("+00:00");
  }

  private void set(String value) throws SQLException {
    try (var statement = connection.prepareStatement("SET time_zone=?")) {
      statement.setString(1, value);
      statement.execute();
    }
  }

  /** 恢复原会话时区，避免向应用连接池泄漏UTC配置。 */
  @Override
  public void close() throws SQLException {
    set(previous);
  }
}
