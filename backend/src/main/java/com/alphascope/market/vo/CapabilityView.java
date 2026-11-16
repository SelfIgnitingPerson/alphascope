package com.alphascope.market.vo;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.market.provider.MarketCapability;
import java.time.Instant;

/**
 * 单个供应商能力的只读观测状态，查询不触发网络。
 *
 * @author chenxuefeng
 */
public record CapabilityView(
    /** 能力代码。 */
    MarketCapability capability,
    /** 代码实现是否支持该能力，不代表账号有权限。 */
    boolean supported,
    /** NOT_CHECKED、AVAILABLE、UNAVAILABLE、COOLDOWN、ACCESS_RESTRICTED或UNSUPPORTED。 */
    String state,
    /** 最近成功时间，未观测时为空。 */
    Instant lastSuccess,
    /** 可公开的失败代码，不含Token、请求正文和原始异常文本。 */
    String lastError,
    /** 冷却结束时间，无冷却时为空。 */
    Instant retryAt,
    /** 最近一次实际失败时间；进程重启后为空，不把保护记录误作新的失败。 */
    Instant lastFailure,
    /** 当次受控诊断；没有捕获异常时根据已记录代码解释，不能补造原文。 */
    FailureDiagnosis diagnostic) {
  public CapabilityView {
    if (diagnostic == null) {
      diagnostic =
          FailureDiagnostics.code(lastError == null || lastError.isBlank() ? state : lastError);
    }
  }
}
