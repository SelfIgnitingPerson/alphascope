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
  /** 只使用不晚于所选日的研究与复核；为空时读取最新业务日。 */
  @jakarta.validation.constraints.PastOrPresent
  @org.springframework.format.annotation.DateTimeFormat(
      iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
  private java.time.LocalDate asOf;

  /** 可选精确证券代码。 */
  @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)")
  private String symbol;

  /** 可选研究周期。 */
  @Pattern(regexp = "SHORT|MEDIUM|LONG")
  private String horizon;

  /** null表示全部，true仅已归档，false仅跟踪中。 */
  private Boolean archived;

  /** {@inheritDoc} */
  @Override
  public void validate() {
    super.validate();
    if (asOf != null
        && (asOf.getYear() < 1990
            || asOf.isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")))))
      throw new IllegalArgumentException("研究日期须在1990年至上海当前日期之间");
  }

  /**
   * SQL分页起点，由受校验页码计算。
   *
   * @return 零起点偏移
   */
  public int getOffset() {
    return (getPage() - 1) * getSize();
  }
}
