package com.alphascope.operations.job;

import com.alphascope.identity.context.TenantExecutor;
import com.alphascope.operations.service.ProductStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期持久运行告警；无外部收件人配置，不发送邮件或消息。
 *
 * @author chenxuefeng
 */
@Component
@ConditionalOnProperty(name = "alphascope.product.monitor-enabled", havingValue = "true")
public class ProductMonitor {
  private final TenantExecutor tenants;
  private final ProductStatusService status;

  public ProductMonitor(TenantExecutor tenants, ProductStatusService status) {
    this.tenants = tenants;
    this.status = status;
  }

  /** 每分钟按各账号自身关注范围检查，异常由统一租户执行入口记录。 */
  @Scheduled(fixedDelay = 60000, initialDelay = 60000, scheduler = "productScheduler")
  public void check() {
    tenants.each(status::observe);
  }
}
