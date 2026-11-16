package com.alphascope.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 业务消费、心跳、截止监控和备份使用独立执行空间，慢任务不占用监控线程。
 *
 * @author chenxuefeng
 */
@Configuration
@EnableScheduling
public class TaskConfig {
  /** 普通日历触发只负责快速提交，不承载业务执行。 */
  @Bean
  public ThreadPoolTaskScheduler taskScheduler() {
    return scheduler("alphascope-trigger-");
  }

  /** 单一业务消费者，保持供应商顺序采集与限流。 */
  @Bean
  public ThreadPoolTaskScheduler workerScheduler() {
    return scheduler("alphascope-worker-");
  }

  /** 心跳连接不与耗时研究或备份竞争执行线程。 */
  @Bean
  public ThreadPoolTaskScheduler heartbeatScheduler() {
    return scheduler("alphascope-heartbeat-");
  }

  /** 截止监控独立运行，不等待业务线程释放调度池。 */
  @Bean
  public ThreadPoolTaskScheduler controlScheduler() {
    return scheduler("alphascope-control-");
  }

  /** 定时与补做备份共用单线程，避免重复并发导出。 */
  @Bean
  public ThreadPoolTaskScheduler backupScheduler() {
    return scheduler("alphascope-backup-");
  }

  /** 研究验证单消费者，与行情研究和备份相互隔离。 */
  @Bean
  public ThreadPoolTaskScheduler validationScheduler() {
    return scheduler("alphascope-validation-");
  }

  /** 验证的心跳和截止控制，不与实际计算共用线程。 */
  @Bean
  public ThreadPoolTaskScheduler validationControlScheduler() {
    return scheduler("alphascope-validation-control-");
  }

  /** 产品运行告警有独立线程，不阻塞任务心跳或截止。 */
  @Bean
  public ThreadPoolTaskScheduler productScheduler() {
    return scheduler("alphascope-product-");
  }

  private ThreadPoolTaskScheduler scheduler(String prefix) {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(prefix);
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }
}
