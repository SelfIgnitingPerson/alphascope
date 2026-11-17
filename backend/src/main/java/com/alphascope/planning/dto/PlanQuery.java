package com.alphascope.planning.dto;

import com.alphascope.common.dto.PageRequest;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 个人计划的服务端筛选与分页，身份不接受客户端提供。
 *
 * @author chenxuefeng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanQuery extends PageRequest {
  /** 可选精确证券代码。 */
  @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)")
  private String symbol;

  /** 可选研究周期。 */
  @Pattern(regexp = "SHORT|MEDIUM|LONG")
  private String horizon;

  /** null表示全部，true仅已归档，false仅跟踪中。 */
  private Boolean archived;

  /**
   * SQL分页起点，由受校验页码计算。
   *
   * @return 零起点偏移
   */
  public int getOffset() {
    return (getPage() - 1) * getSize();
  }
}
