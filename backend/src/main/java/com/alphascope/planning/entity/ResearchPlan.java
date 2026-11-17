package com.alphascope.planning.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class ResearchPlan {
  /** 计划主键。 */
  private Long id;

  /** 证券代码。 */
  private String symbol;

  /** 短中长期周期代码。 */
  private String horizon;

  /** 当期行动方案代码。 */
  private String actionCode;

  /** 不可变完整研究视图精确JSON。 */
  private String snapshotJson;

  /** 原始研究文本SHA256。 */
  private String snapshotHash;

  /** 是否由所属用户归档，不删除证据。 */
  private Boolean archived;

  /** 保存时间，上海时区。 */
  private LocalDateTime createdAt;
}
