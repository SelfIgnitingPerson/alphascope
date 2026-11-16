package com.alphascope.operations.vo;

import java.time.Instant;

/**
 * 最近备份尝试的公开持久状态；失败不会覆盖最后成功的备份指针。
 *
 * @author chenxuefeng
 */
public record BackupRunStatus(
    /** QUEUED、RUNNING、VERIFIED、PARTIAL或FAILED。 */
    String status,
    /** 实际观察时刻。 */
    Instant observedAt,
    /** 本次已验证本机备份文件名。 */
    String fileName,
    /** 本机备份指纹。 */
    String sha256,
    /** 可公开且不包含上游异常正文的原因。 */
    String reason) {}
