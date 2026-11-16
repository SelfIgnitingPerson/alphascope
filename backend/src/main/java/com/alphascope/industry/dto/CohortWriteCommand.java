package com.alphascope.industry.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 披露样本新增命令；主题代码由 SQL 解析为主题主键。
 *
 * @author chenxuefeng
 */
@Data
public class CohortWriteCommand {
  /** 不可覆盖的披露样本版本代码。 */
  private String code;

  /** 所属主题的稳定业务代码。 */
  private String themeCode;

  /** 披露样本标题。 */
  private String title;

  /** 已保存的公开来源文档主键。 */
  private Long sourceDocumentId;

  /** 该版公开材料可用于研究的时刻。 */
  private LocalDateTime availableAt;

  /** 样本生效日期，不得倒填。 */
  private LocalDate effectiveFrom;

  /** 披露的预期成员数量。 */
  private Integer expectedMembers;

  /** 规范化披露定义的 SHA-256。 */
  private String contentHash;

  /** 完整披露定义 JSON。 */
  private String definitionJson;
}
