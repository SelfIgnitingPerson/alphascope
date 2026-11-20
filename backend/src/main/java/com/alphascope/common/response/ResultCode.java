package com.alphascope.common.response;

/**
 * 对外稳定的业务结果码；HTTP 状态由控制器、异常处理器或请求过滤器保留。
 *
 * @author chenxuefeng
 */
public enum ResultCode {
  /** 请求已经成功处理；异步任务仅表示成功入队。 */
  SUCCESS,
  /** 未登录、会话过期或凭据不正确。 */
  UNAUTHENTICATED,
  /** 已登录但没有执行该动作的权限。 */
  FORBIDDEN,
  /** 当前用户任务或保存容量已达上限。 */
  CAPACITY_EXCEEDED,
  /** 上游数据能力未满足当前动作。 */
  DATA_UNAVAILABLE,
  /** 请求字段缺失、格式错误或业务校验不通过。 */
  INVALID_ARGUMENT,
  /** 标的、任务、报告或接口不存在。 */
  NOT_FOUND,
  /** 违反不可覆盖的业务唯一约束。 */
  CONFLICT,
  /** 接口不支持该 HTTP 方法。 */
  METHOD_NOT_ALLOWED,
  /** 同一客户端写入过于频繁。 */
  RATE_LIMITED,
  /** 未公开内部细节的服务异常，使用 requestId 核查日志。 */
  INTERNAL_ERROR
}
