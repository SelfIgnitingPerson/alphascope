package com.alphascope.intelligence.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 同类型、同业务日、同模式的两份不可变报告修订比较。
 *
 * @author chenxuefeng
 */
public record ReportComparison(
    /** 作为比较基准的报告修订ID。 */
    long leftId,

    /** 与基准比较的另一份报告修订ID。 */
    long rightId,

    /** 两份报告冻结输入的内容指纹是否不同。 */
    boolean inputChanged,

    /** 同一业务对象的已保存版本之间的差异列表。 */
    List<Change> changes) {
  /**
   * 两份报告修订中同一个结构化字段的前后值。
   *
   * @author chenxuefeng
   */
  public record Change(
      /** 发生变化的结构化报告字段名称。 */
      String field,

      /** 比较基准修订中保存的原始值。 */
      JsonNode before,

      /** 比较目标修订中保存的原始值。 */
      JsonNode after) {}
}
