package com.alphascope.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 统一分页请求，保留现有 page、size 查询参数及默认值。
 *
 * @author chenxuefeng
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
  /** 页码从 1 开始，最大 100000；省略或空值时为 1。 */
  @Builder.Default
  @Min(1)
  @Max(100000)
  @Schema(defaultValue = "1")
  private Integer page = 1;

  /** 每页 1 至 100 条；省略或空值时为 20。 */
  @Builder.Default
  @Min(1)
  @Max(100)
  @Schema(defaultValue = "20")
  private Integer size = 20;

  /**
   * 读取规范化后的页码，兼容空查询参数。
   *
   * @return 已提供页码，未提供时为 1
   */
  public Integer getPage() {
    return page == null ? 1 : page;
  }

  /**
   * 读取规范化后的分页大小，兼容空查询参数。
   *
   * @return 已提供大小，未提供时为 20
   */
  public Integer getSize() {
    return size == null ? 20 : size;
  }

  /**
   * 校验内部业务调用的分页范围，HTTP 请求同时通过字段约束校验。
   *
   * @throws IllegalArgumentException 页码或分页大小超出公开接口的合法范围
   */
  public void validate() {
    if (getPage() < 1 || getPage() > 100000 || getSize() < 1 || getSize() > 100) {
      throw new IllegalArgumentException("页码须为1至100000，每页条数须为1至100");
    }
  }
}
