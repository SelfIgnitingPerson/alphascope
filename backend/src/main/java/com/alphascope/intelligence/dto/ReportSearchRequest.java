package com.alphascope.intelligence.dto;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.validation.DateRangeValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 报告归档查询；保留 type、start、end、page、size、latestOnly 的公开参数名称。
 *
 * @author chenxuefeng
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReportSearchRequest extends PageRequest {
  /** 报告类型 DAILY 或 WEEKLY；省略或空字符串时为 DAILY。 */
  @Builder.Default
  @NotBlank
  @Pattern(regexp = "DAILY|WEEKLY")
  @Schema(defaultValue = "DAILY")
  private String type = "DAILY";

  /** 可选起始日期；缺省区间由所属业务服务按截止日期确定。 */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate start;

  /** 可选截止日期；省略时采用上海当前日期。 */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate end;

  /** true 仅返回同业务日最新修订；省略或空值时为 true。 */
  @Builder.Default
  @Schema(defaultValue = "true")
  private Boolean latestOnly = true;

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

  /**
   * 兼容原接口空类型参数采用 DAILY 默认值的行为。
   *
   * @param type 客户端报告类型；null 或空字符串表示采用默认类型
   */
  public void setType(String type) {
    this.type = type == null || type.isEmpty() ? "DAILY" : type;
  }

  /**
   * 读取规范化的报告类型。
   *
   * @return 已指定的类型；未指定时为 DAILY
   */
  public String getType() {
    return type == null || type.isEmpty() ? "DAILY" : type;
  }

  /**
   * 读取最新修订筛选标志。
   *
   * @return 已提供标志；未提供时为 true
   */
  public Boolean getLatestOnly() {
    return latestOnly == null ? Boolean.TRUE : latestOnly;
  }
}
