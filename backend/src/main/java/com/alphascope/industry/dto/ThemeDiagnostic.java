package com.alphascope.industry.dto;

import java.util.List;
import lombok.Data;

/**
 * 同一主题连续交易日的覆盖诊断，不能跨主题拼接合格记录。
 *
 * @author chenxuefeng
 */
@Data
public class ThemeDiagnostic {
  /** 主题代码。 */
  private String code;

  /** 主题中文名称。 */
  private String name;

  /** 该业务日真实有效的登记成员数。 */
  private int registeredMembers;

  /** 规则要求的最少成员数。 */
  private int requiredMembers;

  /** 有收益与广度记录的交易日数。 */
  private int recordedDays;

  /** 满足多成员和覆盖条件的交易日数。 */
  private int eligibleDays;

  /** 规则要求的连续交易日数。 */
  private int requiredDays;

  /** 实际交易日历中的要求日期。 */
  private List<String> requiredDates;

  /** 缺少有效观测的日期。 */
  private List<String> missingDates;

  /** 归属实际生效日期，未知保持 UNKNOWN。 */
  private String membershipSince;

  /** 完整行业或披露研究样本的范围。 */
  private String scope;

  /** 所选样本主键；人工归属时为 0。 */
  private long cohortId;

  /** 披露原始链接；人工归属时为空字符串。 */
  private String sourceUrl;

  /** 完整行业覆盖率；未核实保持 UNKNOWN。 */
  private String industryCoverage;
}
