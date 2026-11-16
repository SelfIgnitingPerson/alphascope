package com.alphascope.common.config;

/**
 * 当前生产流水线的版本入口；历史报告继续使用其冻结版本，判定口径变化须显式升级。
 *
 * @author chenxuefeng
 */
public final class ResearchVersion {
  private ResearchVersion() {}

  public static final String REPORT = "REPORT_V11";
  public static final String ALGORITHM = "ADJ_V2/RULE_V2/" + REPORT;
  public static final String PIPELINE = "PIPELINE_V14/" + ALGORITHM;
  public static final String ANALYSIS =
      "FROZEN_FACTS_V8/LINKAGE_V1/ADVICE_V1/OBSERVATION_V1/STORED_HISTORY_V1/PRICE_VOLUME_2M_V3/POSITION_RESEARCH_V1";
}
