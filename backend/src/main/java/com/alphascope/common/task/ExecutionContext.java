package com.alphascope.common.task;

import java.util.function.Consumer;

/**
 * 当前工作线程的取消检查与进度回调；业务模块无需依赖调度实现。
 *
 * @author chenxuefeng
 */
public final class ExecutionContext {
  private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();

  private ExecutionContext() {}

  /**
   * 绑定阶段控制；关闭时恢复外层上下文，不把任务状态泄漏到复用线程。
   *
   * @param check 截止时间与持久取消状态检查
   * @param progress 已节流的阶段进度写入回调
   * @return 需要关闭的线程作用域
   */
  public static Scope bind(Runnable check, Consumer<ExecutionProgress> progress) {
    Binding previous = CURRENT.get();
    CURRENT.set(new Binding(check, progress));
    return new Scope(previous);
  }

  /** 检查当前任务终止要求；无任务时仍响应线程中断。 */
  public static void checkpoint() {
    Binding binding = CURRENT.get();
    if (binding != null) {
      binding.check().run();
    }
    if (Thread.currentThread().isInterrupted()) {
      throw new TaskStoppedException(TaskStopReason.INTERRUPTED);
    }
  }

  /**
   * 发布实际进度；后台控制器负责节流与持久化。
   *
   * @param progress 已处理数量、总量、动作及实际更新时间
   */
  public static void progress(ExecutionProgress progress) {
    checkpoint();
    Binding binding = CURRENT.get();
    if (binding != null) {
      binding.progress().accept(progress);
    }
  }

  /**
   * 当前线程绑定的检查与回调。
   *
   * @author chenxuefeng
   */
  private record Binding(
      /** 本阶段终止检查。 */
      Runnable check,
      /** 本阶段进度回调。 */
      Consumer<ExecutionProgress> progress) {}

  /**
   * 自动恢复调用线程的原有上下文。
   *
   * @author chenxuefeng
   */
  public static final class Scope implements AutoCloseable {
    private final Binding previous;

    private Scope(Binding previous) {
      this.previous = previous;
    }

    /** 释放当前绑定并恢复外层上下文。 */
    @Override
    public void close() {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }
}
