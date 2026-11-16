package com.alphascope.common.exception;

import com.alphascope.common.response.ResultCode;

/**
 * 只携带经过设计的公开领域错误；内部异常仍由日志定位。
 *
 * @author chenxuefeng
 */
public final class BusinessException extends RuntimeException {
  private final ResultCode code;
  private final int status;

  public BusinessException(ResultCode code, int status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public ResultCode getCode() {
    return code;
  }

  public int getStatus() {
    return status;
  }
}
