package com.alphascope.industry.entity;

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
public class SourceDocument {
  /** 记录主键，自增生成 */
  private Long id;

  /** 来源材料类型标识，例如公告、报告或产业材料 */
  private String sourceType;

  /** 原始公开材料URL */
  private String url;

  /** 来源文档标题 */
  private String title;

  /** 原始材料公开发布时间，上海时区 */
  private LocalDateTime publishedAt;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 保留的原文或摘录文本；外部内容不具有系统指令权限 */
  private String content;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;
}
