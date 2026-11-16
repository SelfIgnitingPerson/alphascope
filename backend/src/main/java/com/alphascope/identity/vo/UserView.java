package com.alphascope.identity.vo;

/**
 * 当前身份的公开资料，不包含密码、会话摘要或管理员密钥。
 *
 * @author chenxuefeng
 * @param id 用户与私人研究范围的唯一标识
 * @param username 登录名称
 * @param role USER或ADMIN；管理员仍不能越过私人研究范围
 */
public record UserView(long id, String username, String role) {}
