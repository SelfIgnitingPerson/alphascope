package com.alphascope.operations.vo;

/**
 * 四类不同存储口径：个人、平台逻辑、数据库物理、运行资料磁盘；不混成一个百分比。
 *
 * @author chenxuefeng
 * @param privateBudget 当前用户逻辑预算
 * @param platform 平台及数据库总量
 * @param runtimeUsableBytes 应用运行资料文件系统剩余
 * @param minimumRuntimeFreeBytes 应用写入保护线
 * @param databaseDiskStatus 数据库磁盘监控状态
 */
public record StorageStatus(
    StorageTotals privateBudget,
    StorageTotals platform,
    long runtimeUsableBytes,
    long minimumRuntimeFreeBytes,
    String databaseDiskStatus) {}
