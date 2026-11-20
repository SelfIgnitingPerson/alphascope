package com.alphascope.intelligence.vo;

import java.util.List;

/**
 * 三表经营质量的可追溯明细，不用比率推断未披露的销量或价格原因。
 *
 * @author chenxuefeng
 * @param cashBasis 现金兑现分母口径
 * @param metrics 有明确原始字段的观察
 * @param missing 缺少的材料
 */
public record OperatingQuality(String cashBasis, List<Metric> metrics, List<String> missing) {
  /**
   * 单项财务事实及可核查字段。
   *
   * @author chenxuefeng
   * @param label 中文项目
   * @param value 金额或比例，空值不当零
   * @param unit CNY元或RATIO比例
   * @param sourceField 保存原文的JSON字段路径
   * @param sourceUrl 供应商材料入口
   * @param meaning 解释与适用边界
   */
  public record Metric(
      String label,
      Double value,
      String unit,
      String sourceField,
      String sourceUrl,
      String meaning) {}
}
