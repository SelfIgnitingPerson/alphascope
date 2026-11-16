package com.alphascope.scheduler.dto;

import java.time.LocalDate;
import lombok.Builder;

/**
 * 自动提交的持久范围，用户身份由SQL边界注入，不能来自调度参数。
 *
 * @author chenxuefeng
 */
@Builder
public record ScheduledJobQuery(
    /** DAILY或WEEKLY。 */
    String jobName,
    /** 最近已收盘业务日。 */
    LocalDate businessDate,
    /** 当前真实来源组合或明确演示范围。 */
    String dataScope) {}
