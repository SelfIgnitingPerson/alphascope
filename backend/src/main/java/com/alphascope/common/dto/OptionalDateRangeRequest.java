package com.alphascope.common.dto;

import com.alphascope.common.validation.DateRangeValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 允许省略的日期区间；默认范围由各业务服务统一确定。
 *
 * @author chenxuefeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionalDateRangeRequest {
  /** 可选起始日期；缺省区间由所属业务服务按截止日期确定。 */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate start;

  /** 可选截止日期；省略时采用上海当前日期。 */
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
