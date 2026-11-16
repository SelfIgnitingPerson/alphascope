package com.alphascope.identity.dto;

import com.alphascope.identity.vo.UserView;

/**
 * 只在认证Service与Cookie写入间传递的临时凭据，不作为JSON响应。
 *
 * @author chenxuefeng
 * @param user 公开用户资料
 * @param token 高熵随机会话令牌
 * @param csrf 随机CSRF令牌
 */
public record IssuedSession(UserView user, String token, String csrf) {
  @Override
  public String toString() {
    return "IssuedSession[credentials=REDACTED]";
  }
}
