package com.alphascope.backtest.dto;

import com.alphascope.common.validation.DateRangeValidator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 已保存信号的后续收益查询条件，不包含可由客户端切换的数据源或真实/演示模式。
 *
 * @author chenxuefeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalStatisticsRequest {
  /** 必填统一证券代码，例如 300476.SZ。 */
  @NotBlank private String symbol;

  /** 必填起始日期，ISO 格式 YYYY-MM-DD，包含当日。 */
  @NotNull(message = "开始日期不能为空")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate start;

  /** 必填结束日期，ISO 格式 YYYY-MM-DD，包含当日。 */
  @NotNull(message = "结束日期不能为空")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate end;

  /** 可选收益评估截止日期；省略时为end，不能早于信号区间结束日或晚于上海当前日。 */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate evaluationDate;

  /** 较晚留出区间首日；省略时按信号日期区间的前70%/后30%预先分割。 */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate splitDate;

  /** 默认RETROSPECTIVE回顾当前版本；POINT_IN_TIME严格检查保存和修订时刻。 */
  @Builder.Default private AvailabilityMode availabilityMode = AvailabilityMode.RETROSPECTIVE;

  /** 可选规则版本；未指定且同规则存在多个版本时不混合计算。 */
  @Size(max = 64)
  private String ruleVersion;

  /**
   * 校验同时提供的日期顺序；必填性由字段约束单独判断。
   *
   * @return 日期缺省或起始日期不晚于结束日期时为 true
   */
  @AssertTrue(message = "开始日期不能晚于结束日期")
  @Schema(hidden = true)
  @JsonIgnore
  public boolean isDateOrderValid() {
    return DateRangeValidator.isOrdered(start, end);
  }
}
