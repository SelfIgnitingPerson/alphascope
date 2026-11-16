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
public class MarketFactorSnapshot {
  /** 记录主键，自增生成 */
  private Long id;

  /** 供应商证券代码，如sh.600000或sz.300476，非页面统一后缀格式 */
  private String symbol;

  /** 实际数据供应商或人工来源标识 */
  private String source;

  /** 已核验复权事件覆盖到的交易日期，不是事件首次披露日 */
  private LocalDate verifiedThrough;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 供应商复权事件原文JSON；相同内容保留首次采集证据 */
  private String rawJson;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;

  /** 最近一次确认该内容可覆盖verified_through的时间，上海时区 */
  private LocalDateTime lastVerifiedAt;
}
