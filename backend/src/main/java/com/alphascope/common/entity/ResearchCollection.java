package com.alphascope.common.entity;

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
public class ResearchCollection {
  /** 记录主键，自增生成 */
  private Long id;

  /** 采集数据类别，如财报、市场截面、预测或MARKET_WINDOW行情窗口 */
  private String dataKind;

  /** 采集对象代码或范围标识，例如标的代码或SH_SZ_A */
  private String scope;

  /** 本次研究或采集的业务日期，按上海时区记录 */
  private LocalDate businessDate;

  /** 最近采集状态或复用方式，如SUCCESS、UNAVAILABLE、FRESH_CACHE、STORED_HISTORY、FETCHED */
  private String status;

  /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
  private String evidenceJson;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;
}
