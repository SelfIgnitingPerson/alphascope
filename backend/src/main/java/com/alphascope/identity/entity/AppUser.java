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
public class AppUser {
  /** 账号主键，同时为私人研究范围。 */
  private Long id;

  /** 规范化登录名称。 */
  private String username;

  /** 带算法参数和独立盐的密码摘要。 */
  @ToString.Exclude private String passwordHash;

  /** ADMIN或USER。 */
  private String role;

  /** ACTIVE、DISABLED或PENDING。 */
  private String status;

  /** 账号创建时间。 */
  private LocalDateTime createdAt;
}
