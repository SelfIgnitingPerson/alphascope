package com.alphascope.industry.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class ThemeLeaderRevision {
  /** 不可变修订主键。 */
  private Long id;

  /** 已登记主题代码。 */
  private String themeCode;

  /** 当前目录精确JSON文本。 */
  private String directoryJson;

  /** 文本指纹。 */
  private String contentHash;

  /** 实际维护管理员主键。 */
  private Long reviewerId;

  /** 实际保存时间，上海时区。 */
  private LocalDateTime createdAt;
}
