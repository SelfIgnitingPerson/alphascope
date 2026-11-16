package com.alphascope.market.vo;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.market.provider.MarketCapability;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 一次显式基础能力测试的独立结论；通过不代表其他能力、持续服务或完整历史可用。
 *
 * @author chenxuefeng
 */
public record ProviderProbeView(
    /** 实际被测通道，绝不填写备用通道。 */
    String channel,
    /** 本次核验的独立基础能力。 */
    MarketCapability capability,
    /** 用户明确指定的日期。 */
    LocalDate requestedDate,
    /** SUCCESS、FAILED、SKIPPED或TIMED_OUT；跳过不能当作已连接上游。 */
    String status,
    /** 本次测试开始或本地拦截时间；不冒充原始拒绝时间。 */
    Instant checkedAt,
    /** 本次实际耗时，毫秒。 */
    long elapsedMs,
    /** 成功核验后的摘要；失败或跳过为空，不能填零记录掩盖失败。 */
    ProviderProbeSample sample,
    /** 失败或跳过的受控原因、影响与处理建议；成功为空。 */
    FailureDiagnosis diagnostic,
    /** 本机允许再次测试的最早时刻，不代表供应商限制解除时间。 */
    Instant retryAt) {}
