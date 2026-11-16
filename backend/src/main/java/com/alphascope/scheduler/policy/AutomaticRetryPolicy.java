package com.alphascope.scheduler.policy;

import com.alphascope.scheduler.entity.JobExecution;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 自动调度的保守重试预算；使用持久历史，进程重启不会重置计数。
 *
 * @author chenxuefeng
 */
public final class AutomaticRetryPolicy {
  private AutomaticRetryPolicy() {}

  /**
   * 同用户、同日期、同类型及来源范围最多四次尝试，失败结束至少30分钟后才自动重试。
   *
   * <p>成功、跳过、运行中和用户取消不自动重跑；手动明确重试仍使用原任务入口。
   *
   * @param previous 最近保存尝试
   * @param attempts 当前范围实际尝试数量，包含手动提交
   * @param now 上海业务时间
   * @return 是否可以新增自动尝试
   */
  public static boolean allowed(JobExecution previous, long attempts, LocalDateTime now) {
    return attempts < 4
        && Set.of("FAILED", "INTERRUPTED", "TIMED_OUT").contains(previous.getStatus())
        && previous.getEndedAt() != null
        && !previous.getEndedAt().plusMinutes(30).isAfter(now);
  }
}
