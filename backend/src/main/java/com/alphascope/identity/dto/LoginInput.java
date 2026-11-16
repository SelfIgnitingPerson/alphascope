package com.alphascope.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录输入；不把密码打印到record自动toString。
 *
 * @author chenxuefeng
 * @param username 登录名称
 * @param password 原始密码，仅用于本次校验
 */
public record LoginInput(
    @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {
  @Override
  public String toString() {
    return "LoginInput[credentials=REDACTED]";
  }
}
