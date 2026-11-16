package com.alphascope.scheduler.job;

import com.alphascope.scheduler.service.ScheduledUpdateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 只负责触发统一调度服务；时间采用上海时区，窗口采集及报告由持久队列执行。
 *
 * @author chenxuefeng
 */
@Component
@ConditionalOnProperty(name = "alphascope.scheduler.enabled", havingValue = "true")
public class ReportScheduler {
  private final ScheduledUpdateService updates;

  public ReportScheduler(ScheduledUpdateService updates) {
    this.updates = updates;
  }

  /** 每个自然日收盘时间核对最近交易日，节假日不会伪造当日报告。 */
  @Scheduled(cron = "0 10 16 * * *", zone = "Asia/Shanghai")
  public void daily() {
    updates.daily();
  }

  /** 启动后及休眠恢复后补交最近收盘日；实际自动重试预算由持久历史控制。 */
  @Scheduled(fixedDelay = 1800000, initialDelay = 60000)
  public void catchup() {
    updates.daily();
  }

  /** 周五收盘后提交本周最后已开市日的周报。 */
  @Scheduled(cron = "0 0 18 * * FRI", zone = "Asia/Shanghai")
  public void weekly() {
    updates.weekly();
  }
}
