package com.alphascope.market.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
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
public class MarketBarDO {
  /** 记录主键，自增生成 */
  private Long id;

  /** 标的主键，关联instrument.id */
  private Long instrumentId;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 未复权开盘价，人民币元 */
  private BigDecimal open;

  /** 未复权最高价，人民币元 */
  private BigDecimal high;

  /** 未复权最低价，人民币元 */
  private BigDecimal low;

  /** 未复权收盘价，人民币元 */
  private BigDecimal close;

  /** 成交量，单位股；不得混用手 */
  private BigDecimal volume;

  /** 成交金额，单位人民币元 */
  private BigDecimal amount;

  /** 实际数据供应商或人工来源标识 */
  private String source;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;

  /** 本次行情写入批次标识，用于追溯来源记录 */
  private String batchId;

  /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
  @JsonProperty("is_synthetic")
  private Boolean synthetic;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 严格时点输入的不可变修订ID；普通当前视图为空。 */
  private Long revisionId;

  /** 与当前原价同源的正数复权因子；联查缺失时为空。 */
  private BigDecimal factor;
}
