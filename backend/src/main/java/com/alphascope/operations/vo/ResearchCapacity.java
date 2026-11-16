package com.alphascope.operations.vo;

import java.time.LocalDateTime;

/**
 * 当前用户的资源预算，只返回数量，不包含其他用户资料。
 *
 * @author chenxuefeng
 * @param activeJobs 活动日报周报任务
 * @param activeValidations 活动研究验证任务
 * @param evidenceBytes 已保存完整验证证据字节数
 * @param oldestPendingAt 最早等待时间，未排队时为空
 */
public record ResearchCapacity(
    long activeJobs, long activeValidations, long evidenceBytes, LocalDateTime oldestPendingAt) {}
