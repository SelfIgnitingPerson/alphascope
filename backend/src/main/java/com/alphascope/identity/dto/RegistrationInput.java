package com.alphascope.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 邀请制注册；新用户没有示例自选或其他用户资料。
 *
 * @author chenxuefeng
 * @param username 英文、数字、下划线或短横线的登录名
 * @param password 至少12字符的密码，不记录明文
 * @param invitation 部署方提供的邀请口令
 */
public record RegistrationInput(
    @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") @NotBlank String username,
    @NotBlank @Size(min = 12, max = 128) String password,
    @NotBlank @Size(max = 256) String invitation) {
  @Override
  public String toString() {
    return "RegistrationInput[credentials=REDACTED]";
  }
}
