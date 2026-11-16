package com.alphascope.industry.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 同一主题、日期区间与数据模式下的历史评分查询。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class ThemeScoreQuery {
  /** 主题稳定业务代码，例如 PCB。 */
  private String themeCode;

  /** 评分历史起始日，包含当日。 */
  private LocalDate startDate;

  /** 评分历史结束日，包含当日。 */
  private LocalDate endDate;

  /** 指定真实或演示评分，不跨模式拼接。 */
  private boolean synthetic;
}
