package com.alphascope.industry.vo;

import com.alphascope.industry.entity.Theme;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

/**
 * 对外查询响应；与数据库实体分离，保持单位和业务时点语义。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ThemeView(
    /** 记录主键，自增生成 */
    Long id,

    /** 唯一主题代码，例如PCB、COOLING、CPO */
    String code,

    /** 主题中文名称 */
    String name,

    /** 主题业务范围及研究说明 */
    String description,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 记录最后更新时间，上海时区 */
    LocalDateTime updatedAt) {
  /**
   * 显式转换主题字典记录；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 主题字典记录，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static ThemeView from(Theme row) {
    return new ThemeView(
        row.getId(),
        row.getCode(),
        row.getName(),
        row.getDescription(),
        row.getCreatedAt(),
        row.getUpdatedAt());
  }
}
