package com.alphascope.planning.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 原始价位与当前行情按同源因子换算后的计划复核；不代表已下单。
 *
 * @author chenxuefeng
 * @param planId 所属计划
 * @param date 复核日期
 * @param status 具体复核状态
 * @param explanation 中文变化原因
 * @param adjustedOriginalStop 原风险线换算到当前锚点后的价位，无法核对因子时为空
 * @param close 当前有效收盘价
 * @param currentActionStatus 当前方案行情条件，仅作重新研究参考
 * @param limitations 适用限制
 */
public record PlanReviewView(
    long planId,
    LocalDate date,
    String status,
    String explanation,
    Double adjustedOriginalStop,
    Double close,
    String currentActionStatus,
    List<String> limitations,
    /** 生成本次复核实际使用的原计划和当前研究来源，旧记录缺省不补造。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Set<String> inputSources) {
  /** 旧冻结内容没有来源字段时保持缺省，不为旧证据补造来源。 */
  public PlanReviewView(
      long planId,
      LocalDate date,
      String status,
      String explanation,
      Double adjustedOriginalStop,
      Double close,
      String currentActionStatus,
      List<String> limitations) {
    this(
        planId,
        date,
        status,
        explanation,
        adjustedOriginalStop,
        close,
        currentActionStatus,
        limitations,
        null);
  }
}
