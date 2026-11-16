package com.alphascope.common.response;

import org.slf4j.MDC;

/**
 * 全部业务 HTTP 接口的统一响应。HTTP 状态仍表示协议结果，code 表示稳定业务结果。
 *
 * @author chenxuefeng
 * @param code SUCCESS 表示成功；其他值由错误处理器统一定义
 * @param message 可展示的处理结果说明，不含内部异常或敏感配置
 * @param data 成功时的明确类型业务数据；无返回值及失败时为 null
 * @param requestId 服务端生成的日志追踪编号，与 X-Request-Id 响应头一致
 */
public record Result<T>(ResultCode code, String message, T data, String requestId) {
  /**
   * 构建携带业务数据的成功响应，沿用当前请求追踪编号。
   *
   * @param data 明确类型的业务数据；无数据时可为 null
   * @param <T> 公开业务响应类型
   * @return SUCCESS 统一响应
   */
  public static <T> Result<T> success(T data) {
    return new Result<>(ResultCode.SUCCESS, "操作成功", data, MDC.get("requestId"));
  }

  /**
   * 构建无业务数据的成功响应。
   *
   * @return data 为 null 的 SUCCESS 统一响应
   */
  public static Result<Void> success() {
    return success(null);
  }

  /**
   * 构建失败响应；HTTP 状态由异常处理器或过滤器单独设置。
   *
   * @param code 稳定业务错误码
   * @param message 可公开的错误说明，不含内部异常或密钥
   * @param requestId 与响应头一致的服务端追踪编号
   * @return data 为 null 的失败响应
   */
  public static Result<Void> failure(ResultCode code, String message, String requestId) {
    return new Result<>(code, message, null, requestId);
  }
}
