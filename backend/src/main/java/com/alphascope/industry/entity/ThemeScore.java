package com.alphascope.industry.entity;

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
public class ThemeScore {
  /** 记录主键，自增生成 */
  private Long id;

  /** 主题主键，关联theme.id */
  private Long themeId;

  /** 交易日期，按上海时区记录 */
  private LocalDate tradeDate;

  /** 评分或观察模型标识，用于区分计算口径 */
  private String modelVersion;

  /** 主题强弱评分，范围0至100；覆盖不足或演示模式时为空 */
  private BigDecimal score;

  /** 有效成员20期相对沪深300强度的等权均值，小数存储 */
  private BigDecimal rs20;

  /** 有效成员中收盘价高于MA20的比例，范围0至1；无有效成员时为空 */
  private BigDecimal breadth;

  /** 有效计算成员数除以当日完整成员数，范围0至1 */
  private BigDecimal coverage;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
  private String evidenceJson;

  /** 历史有效成员当日同源复权收益的等权均值，小数存储 */
  private BigDecimal dayReturn;

  /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
  @JsonProperty("is_synthetic")
  private Boolean synthetic;
}
