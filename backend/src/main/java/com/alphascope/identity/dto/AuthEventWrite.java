package com.alphascope.identity.dto;

import com.alphascope.identity.context.TenantContext;

/**
 * 认证审计输入，不记录明文密码、Cookie或邀请口令。
 *
 * @author chenxuefeng
 * @param key 登录名称的SHA256，减少公开身份信息
 * @param success 是否认证成功
 * @param reason 受控原因代码
 * @param actorId 已确认操作身份，失败匿名认证为空
 */
public record AuthEventWrite(String key, boolean success, String reason, Long actorId) {
  public AuthEventWrite(String key, boolean success, String reason) {
    this(key, success, reason, TenantContext.user() == null ? null : TenantContext.id());
  }
}
