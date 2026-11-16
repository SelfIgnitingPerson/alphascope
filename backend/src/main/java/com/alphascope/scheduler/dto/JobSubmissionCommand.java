package com.alphascope.scheduler.dto;

import java.time.LocalDate;
import lombok.Builder;

/**
 * 服务端构建的任务提交命令，原尝试引用不能由HTTP查询参数任意覆盖。
 *
 * @author chenxuefeng
 */
@Builder
public record JobSubmissionCommand(
    /** DAILY日报或WEEKLY周报。 */
    String reportType,
    /** 上海业务日期，不得晚于当前日期。 */
    LocalDate businessDate,
    /** 显式新修订请求，不重复创建仍在收尾的活动任务。 */
    boolean force,
    /** 原失败、取消或超时尝试主键；首次提交为空。 */
    Long retryOf,
    /** 仅由服务端调度器设置；成功不重复提交，自动失败重试有预算与间隔。 */
    boolean automatic) {}
