package com.alphascope.identity.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 数据库记录实体，身份字段不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class AuthSession {
  /** 会话主键。 */
  private Long id;

  /** 认证用户主键。 */
  private Long userId;

  /** 随机会话令牌SHA256；不存明文。 */
  @ToString.Exclude private String tokenHash;

  /** 浏览器CSRF令牌SHA256。 */
  @ToString.Exclude private String csrfHash;

  /** 固定会话过期时间。 */
  private LocalDateTime expiresAt;

  /** 关联用户登录名。 */
  private String username;

  /** 关联用户角色。 */
  private String role;

  /** 关联用户是否启用。 */
  private String status;
}
