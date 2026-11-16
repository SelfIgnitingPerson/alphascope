package com.alphascope.industry.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 保存已核验的人工主题归属区间，不倒填生效日期。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class MembershipWriteCommand {
  /** 证券主键，来源于已登记主数据。 */
  private long instrumentId;

  /** 主题业务代码，由 SQL 解析为主题主键。 */
  private String themeCode;

  /** 真实归属生效日期，包含当日。 */
  private LocalDate effectiveFrom;

  /** 归属结束日期，包含当日；null 表示未设截止。 */
  private LocalDate effectiveTo;
}
