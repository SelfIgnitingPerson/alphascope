package com.alphascope.fundamental.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
public class EarningsForecastSnapshot {
  /** 记录主键，自增生成 */
  private Long id;

  /** 标的主键，关联instrument.id */
  private Long instrumentId;

  /** 实际观测日期，上海时区；不冒充机构预测发布日期 */
  private LocalDate observationDate;

  /** 该预测快照实际首次观测时刻，上海时区；不倒填旧报告 */
  private LocalDateTime availableAt;

  /** 实际数据供应商或人工来源标识 */
  private String source;

  /** 原始公开来源URL，用于核验和追溯 */
  private String sourceUrl;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 公开汇总预测指标JSON，含年度EPS及评级机构数；EPS单位元/股 */
  private String metricsJson;

  /** 供应商原始响应JSON，用于核验来源及保留当次证据 */
  private String rawJson;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;
}
