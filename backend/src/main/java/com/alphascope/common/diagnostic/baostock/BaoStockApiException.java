package com.alphascope.common.diagnostic.baostock;

/**
 * BaoStock已返回非零业务码；与未取得响应的IO异常分开，明确拒绝不自动重试。
 *
 * @author chenxuefeng
 */
public final class BaoStockApiException extends IllegalStateException {
  /** 供应商明确返回的非零数字码。 */
  private final String errorCode;

  /** 捕获的内部原文；公开前必须经过安全模板筛选。 */
  private final String errorMessage;

  public BaoStockApiException(String errorCode, String errorMessage) {
    super("BAOSTOCK_API_ERROR_" + checkedCode(errorCode));
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  private static String checkedCode(String code) {
    if (code == null || !code.matches("[0-9]{1,10}") || code.equals("0")) {
      throw new IllegalArgumentException("BaoStock业务错误码无效");
    }
    return code;
  }

  /**
   * @return 供应商实际返回的非零数字码，不替换为通用不可用
   */
  public String errorCode() {
    return errorCode;
  }

  /**
   * @return 内部原始消息；不得直接写入公开响应或日志，须通过受控映射
   */
  public String errorMessage() {
    return errorMessage;
  }
}
