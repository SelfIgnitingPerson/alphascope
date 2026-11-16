package com.alphascope.signal.entity;

import java.time.LocalDate;
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
public class SignalRevision {
  /** 不可变修订主键。 */
  private Long id;

  /** 标的主键。 */
  private Long instrumentId;

  /** 规则业务日期。 */
  private LocalDate tradeDate;

  /** 稳定规则代码。 */
  private String ruleCode;

  /** 实际规则版本。 */
  private String ruleVersion;

  /** 风险等级。 */
  private String severity;

  /** 评估状态。 */
  private String status;

  /** 冻结规则证据。 */
  private String evidenceJson;

  /** 本次真实阈值。 */
  private String parametersJson;

  /** 本次完整内容SHA256。 */
  private String contentHash;

  /** 数据库生成的实际入库时刻。 */
  private LocalDateTime recordedAt;

  /** 演示数据标志。 */
  private Boolean synthetic;
}
