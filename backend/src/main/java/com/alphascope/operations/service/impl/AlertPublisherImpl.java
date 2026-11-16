package com.alphascope.operations.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.dto.RuntimeAlertEvent;
import com.alphascope.operations.service.AlertPublisher;
import com.alphascope.operations.vo.WorkspaceStatusView;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 显式配置后才向固定许可主机发布最小运维事件；状态变化去重，失败指数退避。
 *
 * @author chenxuefeng
 */
@Service
public class AlertPublisherImpl implements AlertPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(AlertPublisherImpl.class);
  private final ProductProperties properties;
  private final Clock clock;
  private final URI endpoint;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ConcurrentHashMap<Long, Delivery> deliveries = new ConcurrentHashMap<>();

  public AlertPublisherImpl(ProductProperties properties, AccessProperties access, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    if (properties.getAlertWebhookUrl().isBlank()) {
      endpoint = null;
      return;
    }
    endpoint = URI.create(properties.getAlertWebhookUrl());
    if (!"https".equals(endpoint.getScheme())
        || endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getFragment() != null
        || !properties.getAlertWebhookHosts().contains(endpoint.getHost()))
      throw new IllegalArgumentException("运维Webhook需明确HTTPS地址和主机许可名单");
  }

  /** {@inheritDoc} */
  @Override
  public boolean configured() {
    return endpoint != null;
  }

  /** {@inheritDoc} */
  @Override
  public void publish(WorkspaceStatusView value) {
    if (endpoint == null) return;
    long tenant = TenantContext.id();
    Set<String> codes = value.issues().isEmpty() ? Set.of() : Set.of("RESEARCH_RUNTIME_ATTENTION");
    String key = Jsons.hash(Jsons.write(codes));
    Delivery old = deliveries.get(tenant);
    Instant now = clock.instant();
    if (old != null && (old.hash().equals(key) || now.isBefore(old.retryAt()))) return;
    if (old == null && codes.isEmpty()) return;
    // 载荷只含账号数字标识、状态代码和核查时间；不含自选、持仓、来源合同或密钥。
    String payload =
        Jsons.write(
            new RuntimeAlertEvent(
                tenant, codes, codes.isEmpty() ? "RECOVERED" : "ATTENTION", value.checkedAt()));
    String eventId =
        old != null && old.failures() > 0 ? old.eventId() : UUID.randomUUID().toString();
    try {
      var builder =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(4))
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", eventId)
              .POST(HttpRequest.BodyPublishers.ofString(payload));
      if (!properties.getAlertWebhookToken().isBlank())
        builder.header("Authorization", "Bearer " + properties.getAlertWebhookToken());
      var response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() > 299)
        throw new IllegalStateException("WEBHOOK_REJECTED");
      deliveries.put(tenant, new Delivery(key, eventId, 0, Instant.EPOCH));
      LOGGER.info(
          "runtime_alert_delivered tenantId={} state={}",
          tenant,
          codes.isEmpty() ? "RECOVERED" : "ATTENTION");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      retry(tenant, old, now, eventId);
    } catch (Exception error) {
      retry(tenant, old, now, eventId);
      LOGGER.warn(
          "runtime_alert_delivery_failed tenantId={} failureType={}",
          tenant,
          error.getClass().getSimpleName());
    }
  }

  private void retry(long tenant, Delivery old, Instant now, String eventId) {
    int failures = Math.min(6, old == null ? 1 : old.failures() + 1);
    deliveries.put(
        tenant,
        new Delivery(
            old == null ? "" : old.hash(),
            eventId,
            failures,
            now.plusSeconds(Math.min(3600, 60L << failures))));
  }

  /**
   * 最近一次成功状态与有上限的退避，不代替持久运行告警。
   *
   * @author chenxuefeng
   * @param hash 最近成功发布状态
   * @param eventId 同一重试事件的幂等编号；新的状态转换使用新编号
   * @param failures 连续失败次数
   * @param retryAt 最早重试时间
   */
  private record Delivery(String hash, String eventId, int failures, Instant retryAt) {}
}
