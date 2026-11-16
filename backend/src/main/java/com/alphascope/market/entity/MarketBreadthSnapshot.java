package com.alphascope.market.entity;

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
public class MarketBreadthSnapshot {
  /** 记录主键，自增生成 */
  private Long id;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 证券覆盖范围标识，当前SH_SZ_A仅沪深A股，不含北交所 */
  private String universeScope;

  /** 实际数据供应商或人工来源标识 */
  private String source;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 已核对市场指标JSON，含成交额、涨跌家数、覆盖率和状态；金额单位元 */
  private String metricsJson;

  /** 本次证券清单与全量日线原文JSON，用于复核覆盖分母和缺口 */
  private String rawJson;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;
}
