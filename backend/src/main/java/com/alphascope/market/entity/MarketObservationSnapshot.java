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
public class MarketObservationSnapshot {
  /** 记录主键，自增生成 */
  private Long id;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 观察覆盖范围，当前SH_SZ_A；不冒充包含北交所的全A */
  private String universeScope;

  /** 评分或观察模型标识，用于区分计算口径 */
  private String modelVersion;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 分项分析JSON，含趋势、量能、均线广度、覆盖率和缺失原因 */
  private String analysisJson;

  /** 观察输入证据JSON，含日历、基准、参数、截面及因子引用 */
  private String evidenceJson;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;
}
