package com.alphascope.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 固定结构的数据传输对象，禁止使用无类型Map代替。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobInputState {
  /** 不可变行情修订数量及最大ID。 */
  private String prices;

  /** 业务时点可见财报数量及最大ID。 */
  private String financials;

  /** 市场截面最大版本ID。 */
  private Long marketBreadth;

  /** 产业证据数量及最近更新时间。 */
  private String industry;

  /** 人工归属数量及最近更新时间。 */
  private String membership;

  /** 可见披露样本数量及最大ID。 */
  private String cohorts;

  /** 独立市场观察最大版本ID。 */
  private Long marketObservation;

  /** 时点可见预测数量及最大ID。 */
  private String forecasts;
}
