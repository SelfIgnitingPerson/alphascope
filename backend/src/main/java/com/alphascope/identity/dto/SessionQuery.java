package com.alphascope.identity.dto;

import java.time.LocalDateTime;

/**
 * 有效会话查询，持久层不接受原始Cookie。
 *
 * @author chenxuefeng
 * @param tokenHash 会话摘要
 * @param now 上海当前时间
 */
public record SessionQuery(String tokenHash, LocalDateTime now) {}
