package com.alphascope.market.vo;

import com.alphascope.common.entity.ResearchCollection;
import java.time.LocalDate;
import java.util.List;

/**
 * 三所截面与历史能力分开验收，不能把归档天数当成可用均线覆盖。
 *
 * @author chenxuefeng
 * @param date 目标研究日
 * @param source 实际截面价格来源
 * @param archivedDays 近60个交易日已通过目录核对的归档天数
 * @param requiredDays 广度要求的最短交易日数
 * @param missingDates 尚无合格同日归档的交易日
 * @param factorCapability 当前来源是否具备可调用的同源因子能力
 * @param collection 最近一次三所采集的原因及结果
 * @param nextSteps 明确的可执行补齐步骤
 */
public record MarketReadinessView(
    LocalDate date,
    String source,
    int archivedDays,
    int requiredDays,
    List<LocalDate> missingDates,
    boolean factorCapability,
    ResearchCollection collection,
    List<String> nextSteps,
    /** 每项指标分别验收，渠道宣称支持不等于本机数据完整。 */
    List<Metric> metrics) {
  /** 保留旧构造与冻结协议，旧指标状态不倒填。 */
  public MarketReadinessView(
      LocalDate date,
      String source,
      int archivedDays,
      int requiredDays,
      List<LocalDate> missingDates,
      boolean factorCapability,
      ResearchCollection collection,
      List<String> nextSteps) {
    this(
        date,
        source,
        archivedDays,
        requiredDays,
        missingDates,
        factorCapability,
        collection,
        nextSteps,
        List.of());
  }

  /**
   * 单项市场能力及实际证据。
   *
   * @author chenxuefeng
   * @param label 指标
   * @param status AVAILABLE或INSUFFICIENT_DATA
   * @param actual 已核验数量
   * @param required 应有数量
   * @param reason 缺项或可用条件
   */
  public record Metric(String label, String status, int actual, int required, String reason) {}
}
