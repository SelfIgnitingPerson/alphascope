package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 短、中、长期研究与条件式买卖计划；当前视图不冒充冻结报告或已验证收益策略。
 *
 * @author chenxuefeng
 */
@Builder(toBuilder = true)
public record HoldingResearchView(
    /** 方法版本，周期、阈值与本版本绑定。 */
    String version,
    /** 证券代码和名称。 */
    String symbol,
    String name,
    /** 请求日期与实际行情日期，缺少当日行情不替换请求日期。 */
    LocalDate asOf,
    LocalDate quoteDate,
    /** 已保存行情的实际来源，原价与因子必须同源。 */
    String source,
    /** 已验证的当日未复权收盘价，元；不足时为空。 */
    Double close,
    /** 按周期分别说明的数据完整性，不能等同于收益承诺。 */
    List<Horizon> horizons,
    /** 复用既有两月量价与条件策略。 */
    PriceVolumeStudy priceVolume,
    /** 口径、适用前提和执行限制。 */
    List<String> notes,
    /** 本次实际价格、财报及规则输入来源；旧冻结记录为空时不倒填。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Set<String> inputSources,
    /** 当前研究的观察优先级；旧冻结记录缺省，不倒填。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<ResearchPriority> priorities) {
  /**
   * 单一持有周期的判断、行动条件与缺项。
   *
   * @author chenxuefeng
   */
  @Builder
  public record Horizon(
      /** SHORT、MEDIUM、LONG。 */
      String code,
      /** 中文周期名称与本产品定义的观察期限。 */
      String title,
      String period,
      /** CONDITIONAL_MATCH、WAIT、WATCH、RISK_BLOCKED或UNAVAILABLE。 */
      String status,
      /** 当前可执行的研究结论及主要原因。 */
      String conclusion,
      /** 各方案分别核对，不能由没有卖出信号反推买入。 */
      List<Action> actions,
      /** 采用的价格、财报或规则事实。 */
      List<String> evidence,
      /** 尚未接入或不能核实的数据。 */
      List<String> missing) {}

  /**
   * 买入、加仓、减仓或退出的条件与价位，不含订单数量。
   *
   * @author chenxuefeng
   */
  @Builder
  public record Action(
      /** 稳定方案代码。 */
      String code,
      /** 中文方案名称。 */
      String title,
      /** 条件满足仍需人工确认，状态不表示已下单。 */
      String status,
      /** 方案当期解释。 */
      String conclusion,
      /** 买入/加仓观察带；缺估值时不造出长期合理买价。 */
      PriceVolumeStudy.Zone observationZone,
      /** 参考价格与准确用途，如MA60趋势复核或压力下沿。 */
      Double referencePrice,
      String referenceLabel,
      /** 结构性风险退出参考，元；不是保证成交价。 */
      Double stopReference,
      /** ALL建仓或ANY风险组合。 */
      String conditionLogic,
      /** 实测、要求与三态结果，null表示待核验。 */
      List<PositionStrategy.Condition> conditions,
      /** 撤销或重评该方案的条件。 */
      List<String> invalidation,
      /** 系统未核实的个人前提，不与行情条件混淆。 */
      List<String> assumptions) {}
}
