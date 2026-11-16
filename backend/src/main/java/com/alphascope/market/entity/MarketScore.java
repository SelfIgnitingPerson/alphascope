package com.alphascope.market.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MarketScore {
  /** 记录主键，自增生成 */
  private Long id;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 评分或观察模型标识，用于区分计算口径 */
  private String modelVersion;

  /** 全市场综合评分，范围0至100；证据不足时为NULL，不能按0分解释 */
  private BigDecimal score;

  /** 市场状态：UNKNOWN未知、STRONG强、POSITIVE偏强、SIDEWAYS震荡、WEAK偏弱、RISK_OFF低迷 */
  private String state;

  /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
  private String evidenceJson;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
  @JsonProperty("is_synthetic")
  private Boolean synthetic;
}
