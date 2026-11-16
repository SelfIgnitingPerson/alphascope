package com.alphascope.identity.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 访问方式与会话配置；公网部署显式启用PUBLIC，密钥不进入日志。
 *
 * @author chenxuefeng
 */
@Data
@Validated
@ConfigurationProperties(prefix = "alphascope.access")
public class AccessProperties {
  /** LOCAL仅限回环访问；PUBLIC要求每个业务请求具有有效会话。 */
  private Mode mode = Mode.LOCAL;

  /** 允许提交浏览器写请求的完整来源，不能配置通配符。 */
  private List<String> origins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

  /** 公网会话Cookie必须经HTTPS发送；仅LOCAL模式可以显式关闭。 */
  private boolean secureCookie = true;

  /** 新用户注册默认关闭；启用后仍需匹配邀请口令。 */
  private boolean registrationEnabled;

  /** 管理员配置的邀请口令，不向状态接口或日志返回。 */
  @ToString.Exclude private String invitation = "";

  /** 只在首位管理员未建立时使用的名称。 */
  private String bootstrapUsername = "";

  /** 只读环境提供的管理员初始密码；仅一次初始化，不重置已有用户。 */
  @ToString.Exclude private String bootstrapPassword = "";

  /** 空闲会话不延长固定有效期，默认8小时。 */
  @Min(300)
  @Max(86400)
  private int sessionSeconds = 28800;

  /** 同一账号最多保留的未过期会话数量。 */
  @Min(1)
  @Max(20)
  private int maximumSessions = 5;

  /** 每个用户最多保存的自选数量，控制首次采集负载。 */
  @Min(1)
  @Max(100)
  private int maximumWatchlist = 30;

  /** 邀请注册实例的账号容量，防止无限注册耗尽调度与存储资源。 */
  @Min(2)
  @Max(100000)
  private int maximumAccounts = 1000;

  /**
   * 明确的部署访问方式。
   *
   * @author chenxuefeng
   */
  public enum Mode {
    LOCAL,
    PUBLIC
  }
}
