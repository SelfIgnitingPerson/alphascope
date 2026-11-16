package com.alphascope.common.task;

/**
 * 可持久化的任务终止原因，不把主动取消或截止时间当作供应商故障重试。
 *
 * @author chenxuefeng
 */
public enum TaskStopReason {
  CANCELLED,
  TIMED_OUT,
  INTERRUPTED
}
