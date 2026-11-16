package com.alphascope.scheduler.dto;

import java.util.Set;

/**
 * 持久任务执行状态；是否产生完整研究数据由独立dataStatus表达。
 *
 * @author chenxuefeng
 */
public enum JobStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  SKIPPED,
  FAILED,
  INTERRUPTED,
  CANCEL_REQUESTED,
  TIMEOUT_REQUESTED,
  CANCELLED,
  TIMED_OUT;

  /**
   * 判断任务是否仍需执行线程处理或收尾。
   *
   * @return 等待、运行或终止请求状态时为true
   */
  public boolean active() {
    return Set.of(PENDING, RUNNING, CANCEL_REQUESTED, TIMEOUT_REQUESTED).contains(this);
  }

  /**
   * 判断是否允许创建保留父引用的新尝试。
   *
   * @return 失败、中断、取消或超时时为true
   */
  public boolean retryable() {
    return Set.of(FAILED, INTERRUPTED, CANCELLED, TIMED_OUT).contains(this);
  }
}
