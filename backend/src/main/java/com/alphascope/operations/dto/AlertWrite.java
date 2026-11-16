package com.alphascope.operations.dto;

import java.time.LocalDateTime;

/**
 * 当前范围的告警观察写入命令。
 *
 * @author chenxuefeng
 * @param issueKey 事项SHA256
 * @param message 明确事项
 * @param now 当前上海时间
 */
public record AlertWrite(String issueKey, String message, LocalDateTime now) {}
