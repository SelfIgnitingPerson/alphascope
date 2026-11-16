package com.alphascope.scheduler.job;

import com.alphascope.identity.context.TenantExecutor;
import com.alphascope.scheduler.service.JobService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时心跳与队列消费使用不同调度线程；进程重启后未开始的PENDING继续执行。
 *
 * @author chenxuefeng
 */
@Component
@ConditionalOnProperty(
    name = "alphascope.jobs.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JobWorker {
  private final JobService jobs;

  private final TenantExecutor tenants;

  public JobWorker(JobService jobs, TenantExecutor tenants) {
    this.tenants = tenants;
    this.jobs = jobs;
  }

  @Scheduled(fixedDelay = 1000, scheduler = "workerScheduler")
  public void consume() {
    tenants.each(jobs::processNext);
  }

  @Scheduled(fixedDelay = 5000, scheduler = "heartbeatScheduler")
  public void heartbeat() {
    jobs.heartbeat();
  }
}
