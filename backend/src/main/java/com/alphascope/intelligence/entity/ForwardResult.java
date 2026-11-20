package com.alphascope.intelligence.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class ForwardResult {
  /** 结果主键。 */
  private Long id;

  /** 原判断主键。 */
  private Long observationId;

  /** 周期与行动。 */
  private String actionCode;

  /** 交易日窗口。 */
  private int windowDays;

  /** 冻结结果JSON。 */
  private String metricsJson;

  /** 复算输入JSON。 */
  private String inputJson;

  /** 输入及结果联合指纹。 */
  private String contentHash;

  /** 轻量结果独立指纹。 */
  private String metricsHash;

  /** 实际评估时刻。 */
  private LocalDateTime createdAt;
}
