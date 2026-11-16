package com.alphascope.common.diagnostic;

/**
 * 可公开的故障说明；只使用受控错误分类，不携带供应商正文、地址、密钥或堆栈。
 *
 * @author chenxuefeng
 */
public record FailureDiagnosis(
    /** 稳定诊断代码。 */
    String code,
    /** 故障标题。 */
    String title,
    /** 有证据支持的原因；无法确定时明确说明。 */
    String reason,
    /** 对当前数据或任务的影响。 */
    String impact,
    /** 用户可以采取的操作，不代表系统会自动重试。 */
    String action,
    /** 可选供应商数字码、代码释义与原文记录情况；通用错误为空。 */
    ProviderError providerError) {

  /**
   * 构造没有已确认供应商数字码的诊断，不能补造错误码。
   *
   * @param code 系统诊断码
   * @param title 标题
   * @param reason 原因
   * @param impact 影响
   * @param action 处理建议
   */
  public FailureDiagnosis(String code, String title, String reason, String impact, String action) {
    this(code, title, reason, impact, action, null);
  }

  /**
   * 受控供应商错误证据，原文和本地代码释义明确分开。
   *
   * @author chenxuefeng
   */
  public record ProviderError(
      /** 供应商代码。 */
      String provider,
      /** 实际上游数字码。 */
      String code,
      /** 命中安全模板的原文；未记录或未公开时为空。 */
      String message,
      /** 官方SDK代码的本地释义；不能称作本次响应原文。 */
      String meaning,
      /** UPSTREAM_SAFE_TEXT、NOT_RECORDED或WITHHELD。 */
      String messageState) {}
}
