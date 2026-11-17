package com.alphascope.market.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class Instrument {
  /** 记录主键，自增生成 */
  private Long id;

  /** 带交易所后缀的标的代码，例如300476.SZ、000300.SH */
  private String symbol;

  /** 标的中文名称 */
  private String name;

  /** 交易所标识：SH上海，SZ深圳 */
  private String exchange;

  /** 标的类型：STOCK股票，INDEX指数 */
  private String type;

  /** 已知上市日期；NULL表示尚未登记，不据此伪造上市前行情 */
  private LocalDate listedAt;

  /** 已明确的退市日期；NULL表示未取得日期证据。 */
  private LocalDate delistedAt;

  /** 标的启用标志：1启用，0停用 */
  private Boolean active;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;
}
