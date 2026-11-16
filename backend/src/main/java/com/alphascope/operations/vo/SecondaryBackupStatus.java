package com.alphascope.operations.vo;

import java.time.Instant;

/**
 * 第二存储复制的实际结果，同文件系统副本不会被称作异地容灾。
 *
 * @author chenxuefeng
 */
public record SecondaryBackupStatus(
    /** NOT_CONFIGURED、VERIFIED、VERIFIED_SAME_FILESYSTEM或FAILED。 */
    String status,
    /** 实际核验时刻。 */
    Instant observedAt,
    /** 托管备份文件名，未配置时为空。 */
    String fileName,
    /** 已复制并核验的压缩文件SHA256。 */
    String sha256,
    /** 是否确实为不同文件系统。 */
    boolean differentFileSystem,
    /** 可公开原因，不包含凭据。 */
    String reason) {}
