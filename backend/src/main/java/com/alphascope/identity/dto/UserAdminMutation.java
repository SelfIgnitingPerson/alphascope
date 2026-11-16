package com.alphascope.identity.dto;

/**
 * 管理员经过校验后的账号变更，不接受角色提升字段。
 *
 * @author chenxuefeng
 * @param id 被管理账号
 * @param status 启用状态
 * @param passwordHash 可空的密码摘要
 */
public record UserAdminMutation(long id, String status, String passwordHash) {
  @Override
  public String toString() {
    return "UserAdminMutation[credentials=REDACTED]";
  }
}
