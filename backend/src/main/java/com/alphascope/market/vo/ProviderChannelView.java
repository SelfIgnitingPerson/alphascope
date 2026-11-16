package com.alphascope.market.vo;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import java.time.Instant;
import java.util.List;

/**
 * 单个行情通道的只读运行状态。
 *
 * @author chenxuefeng
 */
public record ProviderChannelView(
    /** 通道代码。 */
    String channel,

    /** 是否支持指数行情。 */
    Boolean supportsIndexBars,

    /** 通道可用状态，查询不发起网络探测。 */
    String state,

    /** 连续失败次数。 */
    Integer consecutiveFailures,

    /** 最近成功时间。 */
    Instant lastSuccess,

    /** 最近失败代码。 */
    String lastError,

    /** 冷却期结束时间。 */
    Instant retryAt,

    /** 是否为演示数据。 */
    boolean isSynthetic,

    /** 各项能力的支持和观测状态；不通过查询发起网络请求。 */
    List<CapabilityView> capabilities) {

  /** 最近错误的受控解释，不触发请求；支持声明与最近错误分开判断。 */
  @com.fasterxml.jackson.annotation.JsonProperty("diagnostic")
  public FailureDiagnosis diagnostic() {
    if (capabilities != null && lastError != null && !lastError.isBlank()) {
      var observed =
          capabilities.stream()
              .filter(
                  value ->
                      value.supported()
                          && lastError.equals(value.lastError())
                          && value.diagnostic() != null)
              .findFirst();
      if (observed.isPresent()) return observed.get().diagnostic();
    }
    return FailureDiagnostics.code(lastError == null || lastError.isBlank() ? state : lastError);
  }

  /**
   * 保留基础行情供应商的构造入口，研究能力由业务层组合。
   *
   * @param channel 供应商代码
   * @param supportsIndexBars 是否支持指数
   * @param state 基础通道状态
   * @param consecutiveFailures 连续失败数
   * @param lastSuccess 最近成功时间
   * @param lastError 公开错误码
   * @param retryAt 冷却结束时间
   * @param isSynthetic 是否演示
   */
  public ProviderChannelView(
      String channel,
      Boolean supportsIndexBars,
      String state,
      Integer consecutiveFailures,
      Instant lastSuccess,
      String lastError,
      Instant retryAt,
      boolean isSynthetic) {
    this(
        channel,
        supportsIndexBars,
        state,
        consecutiveFailures,
        lastSuccess,
        lastError,
        retryAt,
        isSynthetic,
        List.of());
  }
}
