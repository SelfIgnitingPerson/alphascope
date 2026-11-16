package com.alphascope.common.exception;

/**
 * 请求的标的、报告或任务不存在时使用的业务异常。
 *
 * @author chenxuefeng
 */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
