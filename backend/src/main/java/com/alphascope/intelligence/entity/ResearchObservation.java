package com.alphascope.intelligence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class ResearchObservation {
  /** 不可变观察主键。 */
  private Long id;

  /** 证券代码。 */
  private String symbol;

  /** 研究业务日。 */
  private LocalDate businessDate;

  /** LIVE当日形成或RETROSPECTIVE事后形成。 */
  private String captureKind;

  /** 冻结研究与协议正文。 */
  private String evidenceJson;

  /** 正文指纹。 */
  private String evidenceHash;

  /** 实际保存时刻。 */
  private LocalDateTime createdAt;
}
