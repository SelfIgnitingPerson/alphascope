package com.alphascope.intelligence.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
public class ReportSource {
  /** 记录主键，自增生成 */
  private Long id;

  /** 报告主键，关联analysis_report.id */
  private Long reportId;

  /** 引用的来源文档ID；可与data_reference二选一或同时提供 */
  private Long sourceDocumentId;

  /** 来源批次或数据记录引用字符串；与source_document_id至少填写一项 */
  private String dataReference;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;
}
