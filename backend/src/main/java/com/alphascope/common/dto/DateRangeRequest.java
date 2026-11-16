package com.alphascope.common.dto;

import com.alphascope.common.validation.DateRangeValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 必填日期闭区间查询条件；仅包含客户端允许指定的业务日期，不包含数据源或模式。
 *
 * @author chenxuefeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateRangeRequest {
  /** 必填起始日期，ISO 格式 YYYY-MM-DD，包含当日。 */
  @NotNull(message = "开始日期不能为空")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate start;

  /** 必填结束日期，ISO 格式 YYYY-MM-DD，包含当日。 */
  @NotNull(message = "结束日期不能为空")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate end;

  /**
   * 校验同时提供的日期顺序；必填性由字段约束单独判断。
   *
   * @return 日期缺省或起始日期不晚于结束日期时为 true
   */
  @AssertTrue(message = "开始日期不能晚于结束日期")
  @Schema(hidden = true)
  public boolean isDateOrderValid() {
    return DateRangeValidator.isOrdered(start, end);
  }
}
