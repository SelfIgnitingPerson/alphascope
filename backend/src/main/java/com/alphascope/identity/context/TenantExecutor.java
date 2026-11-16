package com.alphascope.identity.context;

import com.alphascope.identity.mapper.AuthMapper;
import com.alphascope.identity.service.AuthService;
import com.alphascope.operations.config.ProductProperties;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台入口按已启用身份恢复范围；轮换起点，单个用户故障不阻断其他用户。
 *
 * @author chenxuefeng
 */
@Component
public class TenantExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(TenantExecutor.class);
  private final AuthService auth;
  private AuthMapper audit;
  private ProductProperties auditProperties;

  @Autowired
  public void setAudit(AuthMapper audit, ProductProperties properties) {
    this.audit = audit;
    this.auditProperties = properties;
  }

  private final AtomicInteger cursor = new AtomicInteger();

  public TenantExecutor(AuthService auth) {
    this.auth = auth;
  }

  /** 每小时只清理一批过期认证审计，避免长期增长阻塞业务。 */
  @Scheduled(fixedDelay = 3600000, initialDelay = 3600000, scheduler = "productScheduler")
  public void purgeAudit() {
    if (audit != null)
      audit.purgeEvents(
          LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
              .minusDays(auditProperties.getAuthenticationRetentionDays()));
  }

  /**
   * 逐个执行已启用用户的轻量领取或控制动作。
   *
   * @param action 不接受客户端提供用户号的业务动作
   */
  public void each(Runnable action) {
    var users = auth.activeUsers();
    if (users.isEmpty()) return;
    int start = Math.floorMod(cursor.getAndIncrement(), users.size());
    for (int n = 0; n < users.size(); n++) {
      var user = users.get((start + n) % users.size());
      try (var ignored = TenantContext.bind(user)) {
        action.run();
      } catch (RuntimeException error) {
        LOGGER.error(
            "tenant_task_failed tenantId={} type={}",
            user.id(),
            error.getClass().getSimpleName(),
            error);
      }
    }
  }
}
