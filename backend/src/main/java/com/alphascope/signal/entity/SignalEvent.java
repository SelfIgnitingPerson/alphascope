package com.alphascope.signal.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SignalEvent {
  /** 记录主键，自增生成 */
  private Long id;

  /** 标的主键，关联instrument.id */
  private Long instrumentId;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 风险规则代码，如TREND_BREAK_20、PROFIT_SLOWDOWN */
  private String ruleCode;

  /** 规则及阈值配置标识，用于追溯判断口径 */
  private String ruleVersion;

  /** 风险严重程度：HIGH高、MEDIUM中、LOW低 */
  private String severity;

  /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
  private String evidenceJson;

  /** 评估状态：TRIGGERED触发、NOT_TRIGGERED未触发、INSUFFICIENT_DATA不足、NOT_APPLICABLE不适用；演示见is_synthetic */
  private String status;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
  @JsonProperty("is_synthetic")
  private Boolean synthetic;

  /** 严格时点输入的不可变修订ID；普通当前视图为空。 */
  private Long revisionId;
}
