package com.alphascope.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员账号恢复或停用输入，密码字段不会进入自动toString。
 *
 * @author chenxuefeng
 * @param status ACTIVE或DISABLED
 * @param password 可空；提供时至少12字符并撤销全部旧会话
 */
public record UserAdminInput(
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
    @Size(min = 12, max = 128) String password) {
  @Override
  public String toString() {
    return "UserAdminInput[credentials=REDACTED]";
  }
}
