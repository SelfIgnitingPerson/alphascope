package com.alphascope.portfolio.vo;

/**
 * 已加入自选与初始化排队结果分别表达，后续采集失败不会伪装成添加失败。
 *
 * @author chenxuefeng
 * @param symbol 已添加代码
 * @param jobId 首次研究任务，未提交时为空
 * @param message 队列或下一步说明
 */
public record WatchDiscoveryResult(String symbol, Long jobId, String message) {}
