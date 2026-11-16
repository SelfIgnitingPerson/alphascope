package com.alphascope.backtest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 显式研究验证请求；日期、范围和证券均在任务接收时冻结，不允许客户端切换数据源。
 *
 * @author chenxuefeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchValidationRequest {
  /** 信号研究起始日期，不含固定四个月指标预热。 */
  @NotNull private LocalDate start;

  /** 收益评估截止日期，不使用其后的数据。 */
  @NotNull private LocalDate end;

  /** ALL全部、TECHNICAL技术规则或MARKET完整市场评分。 */
  @NotNull @Builder.Default private Scope scope = Scope.ALL;

  /** 明确自选代码；接收任务时缺省从已启用自选冻结，最多十只。 */
  @Size(max = 10)
  @Builder.Default
  private List<@Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String> symbols = List.of();

  /**
   * 研究范围，市场检验和技术回顾分别给出结论。
   *
   * @author chenxuefeng
   */
  public enum Scope {
    ALL,
    TECHNICAL,
    MARKET
  }

  /**
   * 校验区间顺序，十年上限和当前日期另由服务校验。
   *
   * @return 缺省由字段必填约束处理，其余须顺序正确
   */
  @AssertTrue(message = "开始日期不能晚于结束日期")
  @JsonIgnore
  public boolean isDateOrderValid() {
    return start == null || end == null || !start.isAfter(end);
  }
}
