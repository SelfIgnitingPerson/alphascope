package com.alphascope.operations.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 每位用户的产品配额与证据预算，超限拒绝新增而不删除历史。
 *
 * @author chenxuefeng
 */
@Data
@Validated
@ConfigurationProperties(prefix = "alphascope.product")
public class ProductProperties {
  /** 单用户最多等待或执行中的日报周报数。 */
  @Min(1)
  @Max(100)
  private int maximumJobs = 20;

  /** 全平台最多活动研究任务，防止多个账号合计绕过单用户限额。 */
  @Min(1)
  @Max(10000)
  private int maximumPlatformJobs = 200;

  /** 全平台最多活动验证，CPU仍按单消费者执行。 */
  @Min(1)
  @Max(10000)
  private int maximumPlatformValidations = 100;

  /** 全平台证据预算，默认10GiB。 */
  @Min(33554432)
  private long maximumPlatformEvidenceBytes = 10737418240L;

  /** 单用户完整验证证据预算，默认1GiB。 */
  @Min(33554432)
  private long maximumEvidenceBytes = 1073741824L;

  /** 可选的运维Webhook；空值时不产生外部请求。 */
  @lombok.ToString.Exclude private String alertWebhookUrl = "";

  /** 可选Webhook鉴权Token，不写入日志。 */
  @lombok.ToString.Exclude private String alertWebhookToken = "";

  /** 必须显式允许的Webhook主机名，防止任意回调地址。 */
  private List<String> alertWebhookHosts = List.of();

  /** 是否要求收集和展示来源具有管理员登记的对应授权。 */
  private boolean enforceSourceLicense;

  /** 失败审计仅保留受控原因，认证限流证据默认保留30天。 */
  @Min(7)
  @Max(365)
  private int authenticationRetentionDays = 30;
}
