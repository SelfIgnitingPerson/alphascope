package com.alphascope.identity.vo;

import java.time.LocalDateTime;

/**
 * 管理员可见的账号支持资料，不包含私人业务及凭据。
 *
 * @author chenxuefeng
 * @param id 账号主键
 * @param username 登录名称
 * @param role ADMIN或USER
 * @param status ACTIVE、DISABLED或PENDING
 * @param createdAt 创建时间
 */
public record UserAdminView(
    long id, String username, String role, String status, LocalDateTime createdAt) {}
