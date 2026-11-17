package com.alphascope.common.config;

import java.util.Map;

/**
 * 独立维护CLI与应用共用的MySQL连接变量；本机默认使用127.0.0.1，Spring完整配置优先。
 *
 * @author chenxuefeng
 */
public record DatabaseConnectionSettings(
    /** JDBC完整地址，不含账号密码。 */
    String url,
    /** 应用专用数据库账号。 */
    String username,
    /** 只供建立连接，禁止输出到日志或配置摘要。 */
    String password) {

  /**
   * 按与Spring数据源相同的优先级构造维护工具连接，不隐式启动Docker。
   *
   * @param environment 已由项目启动脚本加载的本机变量
   * @return 密码不为空的连接条件
   */
  public static DatabaseConnectionSettings fromEnvironment(Map<String, String> environment) {
    String password =
        value(environment, "SPRING_DATASOURCE_PASSWORD", value(environment, "MYSQL_PASSWORD", ""));
    if (password.isBlank())
      throw new IllegalStateException("数据库密码未配置，请在本机设置MYSQL_PASSWORD或SPRING_DATASOURCE_PASSWORD");
    String url =
        value(
            environment,
            "SPRING_DATASOURCE_URL",
            "jdbc:mysql://"
                + value(environment, "MYSQL_HOST", "127.0.0.1")
                + ":"
                + value(environment, "MYSQL_PORT", "3306")
                + "/"
                + value(environment, "MYSQL_DATABASE", "alphascope")
                + "?connectionTimeZone=Asia/Shanghai&connectTimeout=10000&socketTimeout=30000");
    return new DatabaseConnectionSettings(
        url,
        value(
            environment,
            "SPRING_DATASOURCE_USERNAME",
            value(environment, "MYSQL_USER", "alphascope")),
        password);
  }

  /**
   * 一次性结构维护只读取明确提供的管理凭据，缺少时在任何DDL前停止。
   *
   * @param environment 本机或迁移容器变量
   * @return 独立维护连接，不回退应用密码
   */
  public static DatabaseConnectionSettings forMigration(Map<String, String> environment) {
    var application = fromEnvironment(environment);
    String password = environment.getOrDefault("MYSQL_MIGRATION_PASSWORD", "");
    if (password.isBlank()) password = environment.getOrDefault("LOCAL_MYSQL_ADMIN_PASSWORD", "");
    if (password.isBlank())
      throw new IllegalStateException(
          "尚未配置一次性数据库管理连接；请在本机配置MYSQL_MIGRATION_USER/PASSWORD或LOCAL_MYSQL_ADMIN_USER/PASSWORD，再执行结构维护。未执行DDL，不会使用应用密码代替。");
    String username = environment.getOrDefault("MYSQL_MIGRATION_USER", "");
    if (username.isBlank()) username = environment.getOrDefault("LOCAL_MYSQL_ADMIN_USER", "root");
    if (username.isBlank()) username = "root";
    return new DatabaseConnectionSettings(application.url(), username, password);
  }

  /** 缺省使用统一默认值，已经显式设置的值不擅自替换。 */
  private static String value(Map<String, String> environment, String key, String fallback) {
    return environment.getOrDefault(key, fallback);
  }

  /**
   * 隐去密码，防止调试打印配置时泄漏数据库凭据。
   *
   * @return 不含连接串和密码的配置摘要
   */
  @Override
  public String toString() {
    return "DatabaseConnectionSettings[url=<configured>, username="
        + username
        + ", password=<redacted>]";
  }
}
