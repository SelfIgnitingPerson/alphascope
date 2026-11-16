package com.alphascope.backtest.job;

import com.alphascope.backtest.service.ValidationTaskService;
import com.alphascope.identity.context.TenantExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 独立验证消费者与控制时钟，测试或工具进程可统一关闭队列消费。
 *
 * @author chenxuefeng
 */
@Component
@ConditionalOnProperty(
    name = "alphascope.jobs.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ValidationWorker {
  private final ValidationTaskService tasks;

  private final TenantExecutor tenants;

  public ValidationWorker(ValidationTaskService tasks, TenantExecutor tenants) {
    this.tenants = tenants;
    this.tasks = tasks;
  }

  /** 每次只领取一个持久验证请求。 */
  @Scheduled(fixedDelay = 1000, scheduler = "validationScheduler")
  public void consume() {
    tenants.each(tasks::processNext);
  }

  /** 截止与心跳不等待计算线程空闲。 */
  @Scheduled(fixedDelay = 1000, scheduler = "validationControlScheduler")
  public void monitor() {
    tenants.each(tasks::monitor);
  }
}
