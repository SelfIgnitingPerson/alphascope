package com.alphascope.indicator.entity;

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
public class IndicatorSnapshot {
  /** 记录主键，自增生成 */
  private Long id;

  /** 标的主键，关联instrument.id */
  private Long instrumentId;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 计算算法标识，用于追溯当时采用的计算口径 */
  private String algorithmVersion;

  /** 最近5个有K线交易日复权收盘均价，人民币元；含当日，不足为空 */
  private BigDecimal ma5;

  /** 最近10个有K线交易日复权收盘均价，人民币元；含当日，不足为空 */
  private BigDecimal ma10;

  /** 最近20个有K线交易日复权收盘均价，人民币元；含当日，不足为空 */
  private BigDecimal ma20;

  /** 最近60个有K线交易日复权收盘均价，人民币元；含当日，不足为空 */
  private BigDecimal ma60;

  /** 14期Wilder平均真实波幅，人民币元；历史不足时为空 */
  private BigDecimal atr14;

  /** 20期相对沪深300的收益比值减1，小数存储；日期无法对齐时为空 */
  private BigDecimal rs20;

  /** 当日成交量除以前20个有K线交易日均量，单位倍；均量不含当日 */
  private BigDecimal volRatio;

  /** 指标完整性：COMPLETE完整，INSUFFICIENT_HISTORY历史不足 */
  private String dataStatus;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
  @JsonProperty("is_synthetic")
  private Boolean synthetic;

  /** 计算或生成时完整输入的SHA-256指纹 */
  private String inputHash;

  /** 指标输入序列最后一条行情的批次标识 */
  private String sourceBatch;
}
