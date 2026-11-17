package com.alphascope.operations.vo;

/**
 * 平台资源总量，只包含数字，不包含任何用户标识或私人内容。
 *
 * @author chenxuefeng
 * @param activeJobs 全平台活动研究任务数
 * @param activeValidations 全平台活动验证数
 * @param evidenceBytes 全平台共享和私人正文总字节数
 */
public record PlatformUsage(long activeJobs, long activeValidations, long evidenceBytes) {}
