package com.alphascope.backtest.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
public class ResearchValidationTask {
  /** 验证任务主键，自增生成。 */
  private Long id;

  /**
   * PENDING排队、RUNNING执行、CANCEL_REQUESTED请求取消、TIMEOUT_REQUESTED请求超时、SUCCESS完成、CANCELLED取消、FAILED失败、TIMED_OUT超时、INTERRUPTED失联。
   */
  private String status;

  /** 验证类型：ALL全部、TECHNICAL技术规则、MARKET完整市场。 */
  private String scope;

  /** 任务接收时的数据源与真实模式指纹，不含密钥。 */
  private String dataScope;

  /** 相同活动请求的SHA256去重键，终态清空以允许明确重跑。 */
  private String activeKey;

  /** 任务接收时冻结的日期、证券、验证范围、规则版本及阈值。 */
  private String requestJson;

  /** 当前执行进程的随机归属令牌，最终写入必须匹配。 */
  private String ownerToken;

  /** 当前阶段实际完成数量。 */
  private Integer completedUnits;

  /** 当前阶段总量，0表示尚未确定。 */
  private Integer totalUnits;

  /** 当前处理动作或失败摘要，不包含密钥或完整原文。 */
  private String message;

  /** 轻量验证摘要，不含原始价格、逐日证据或大信号数组。 */
  private String summaryJson;

  /** 完整冻结研究证据，仅显式下载；最大容量由配置限制。 */
  private String evidenceJson;

  /** 完整证据文本SHA256，下载和读取时核对。 */
  private String evidenceSha256;

  /** 接收任务时间，上海时区。 */
  private LocalDateTime createdAt;

  /** 实际开始执行时间，上海时区。 */
  private LocalDateTime startedAt;

  /** 最终完成、取消、超时或失败时间，上海时区。 */
  private LocalDateTime finishedAt;

  /** 执行进程最近心跳，上海时区。 */
  private LocalDateTime heartbeatAt;

  /** 执行硬截止时间，上海时区，不随心跳延长。 */
  private LocalDateTime deadlineAt;

  /** 记录最后更新时间，上海时区。 */
  private LocalDateTime updatedAt;
}
