package com.alphascope.common.task;

import lombok.Getter;

/**
 * 协作式终止执行的控制异常，必须穿过供应商降级和可选采集边界。
 *
 * @author chenxuefeng
 */
@Getter
public final class TaskStoppedException extends RuntimeException {
  private final TaskStopReason reason;

  public TaskStoppedException(TaskStopReason reason) {
    super(
        switch (reason) {
          case CANCELLED -> "用户已请求取消任务";
          case TIMED_OUT -> "当前阶段超过配置执行时限";
          case INTERRUPTED -> "执行线程已中断";
        });
    this.reason = reason;
  }
}
