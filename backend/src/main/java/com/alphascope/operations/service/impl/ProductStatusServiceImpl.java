package com.alphascope.operations.service.impl;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.util.Jsons;
import com.alphascope.operations.dto.AlertWrite;
import com.alphascope.operations.mapper.AlertMapper;
import com.alphascope.operations.service.AlertPublisher;
import com.alphascope.operations.service.CapacityService;
import com.alphascope.operations.service.OperationsStatusService;
import com.alphascope.operations.service.ProductStatusService;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.operations.vo.ProductStatusView;
import com.alphascope.operations.vo.WorkspaceStatusView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 数据日期、队列和授权缺项独立于health；没有凭据或持续运行证据时不标记已就绪。
 *
 * @author chenxuefeng
 */
@Service
public class ProductStatusServiceImpl implements ProductStatusService {
  private final OperationsStatusService operations;
  private final CapacityService capacity;
  private final SourceLicenseService licenses;
  private final AlphaScopeProperties configuration;
  private final AlertMapper alerts;
  private final Clock clock;
  private final TransactionTemplate tx;
  private AlertPublisher publisher;

  @Autowired
  public void setPublisher(AlertPublisher publisher) {
    this.publisher = publisher;
  }

  public ProductStatusServiceImpl(
      OperationsStatusService operations,
      CapacityService capacity,
      SourceLicenseService licenses,
      AlphaScopeProperties configuration,
      AlertMapper alerts,
      Clock clock,
      TransactionTemplate tx) {
    this.operations = operations;
    this.capacity = capacity;
    this.licenses = licenses;
    this.configuration = configuration;
    this.alerts = alerts;
    this.clock = clock;
    this.tx = tx;
  }

  /** {@inheritDoc} */
  @Override
  public WorkspaceStatusView workspace() {
    var status = operations.status();
    return new WorkspaceStatusView(
        status.checkedAt(),
        status.status(),
        status.expectedTradeDate(),
        status.calendarStatus(),
        status.instruments(),
        capacity.status(),
        status.issues());
  }

  /** {@inheritDoc} */
  @Override
  public ProductStatusView product() {
    var workspace = workspace();
    var permissions = licenses.status();
    var blockers = new ArrayList<>(workspace.issues());
    for (var source : permissions)
      if (!source.status().equals("DECLARED_BY_OPERATOR"))
        blockers.add(source.source() + "许可状态：" + source.status());
    blockers.add("需要在实际域名、服务负载和连续交易日下验收；本机测试不证明公网运行或模型稳定有效");
    String hash;
    try (var stream = getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
      hash =
          Jsons.hash(
              new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new IllegalStateException("无法读取运行基线", error);
    }
    return new ProductStatusView(
        workspace,
        permissions,
        "TENANT_V1",
        hash,
        configuration.getOperations().isBackupEnabled(),
        licenses.isEnforced(),
        blockers);
  }

  /** {@inheritDoc} */
  @Override
  public void observe() {
    var value = workspace();
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
    tx.executeWithoutResult(
        status -> {
          for (var issue : value.issues())
            alerts.observe(new AlertWrite(Jsons.hash(issue), issue, now));
          alerts.resolveBefore(now);
        });
    if (publisher != null) publisher.publish(value);
  }
}
