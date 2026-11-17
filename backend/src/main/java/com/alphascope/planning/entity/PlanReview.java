package com.alphascope.planning.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class PlanReview {
  /** 复核记录主键。 */
  private Long id;

  /** 所属计划主键。 */
  private Long planId;

  /** 当前复核结果精确JSON。 */
  private String reviewJson;

  /** 复核文本SHA256。 */
  private String reviewHash;

  /** 复核保存时间，上海时区。 */
  private LocalDateTime createdAt;
}
