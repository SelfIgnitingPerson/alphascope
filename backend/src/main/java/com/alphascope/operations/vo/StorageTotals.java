package com.alphascope.operations.vo;

/**
 * 平台逻辑计量和数据库物理分配，均为聚合数字，不含私人内容。
 *
 * @author chenxuefeng
 * @param usedBytes 已用逻辑正文
 * @param reservedBytes 活动任务预留
 * @param limitBytes 平台逻辑上限
 * @param databaseAllocatedBytes 估算物理表与索引分配
 */
public record StorageTotals(
    long usedBytes, long reservedBytes, long limitBytes, long databaseAllocatedBytes) {}
