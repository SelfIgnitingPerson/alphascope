package com.alphascope.fundamental.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class ResearchEvidenceRecord {
  /** 不可变记录主键。 */
  private Long id;

  /** 标准证券代码。 */
  private String symbol;

  /** 业务日期，行动除权日或假设研究日。 */
  private LocalDate businessDate;

  /** 精确输入证据JSON。 */
  private String evidenceJson;

  /** 输入SHA256。 */
  private String evidenceHash;

  /** 实际登记时刻。 */
  private LocalDateTime createdAt;
}
