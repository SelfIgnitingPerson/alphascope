package com.alphascope.intelligence.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前身份实际留档信号的服务端累计，保留版本分组和全部排除原因。
 *
 * @author chenxuefeng
 * @param date 截止日
 * @param observationCount 已读取实际观察数
 * @param candidateObservations 至少一种入场规则满足条件的留档数
 * @param noEntryReasons 未形成候选的留档按主要原因汇总
 * @param truncated 是否达到观察读取上限，不冒充完整全量
 * @param groups 同方法同源同执行协议的分组
 * @param limitations 适用边界
 * @param inputSources 实际研究与价格来源
 */
public record ForwardValidationView(
    LocalDate date,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) int observationCount,
    int candidateObservations,
    Map<String, Integer> noEntryReasons,
    boolean truncated,
    List<Group> groups,
    List<String> limitations,
    Set<String> inputSources) {
  /**
   * 同版本协议及原行动的独立统计。
   *
   * @author chenxuefeng
   * @param methodVersion 判断规则
   * @param source 实际价格源
   * @param horizon 原周期
   * @param actionCode 原行动
   * @param protocolVersion 冻结执行协议
   * @param windows 5、10、20交易日结果
   */
  public record Group(
      String methodVersion,
      String source,
      String horizon,
      String actionCode,
      String protocolVersion,
      List<Statistics> windows) {}

  /**
   * 已冻结成熟结果的描述统计；无交易或无亏损时相关指标保留未知。
   *
   * @author chenxuefeng
   * @param windowDays 市场交易日窗口
   * @param status COLLECTING或DESCRIPTIVE_ONLY，均不证明有效性
   * @param calculated 已冻结且非重叠的可试算样本
   * @param attempted 非重叠成熟尝试，包含未成交
   * @param meanNetReturn 扣费平均收益
   * @param positiveRatio 正收益比例，非实盘胜率
   * @param profitLossRatio 正收益均值除亏损绝对均值；任一侧缺失为空
   * @param worstDrawdown 已核验样本的最大日收盘净权益回撤
   * @param meanExcessReturn 对齐指数价格收益的平均差，策略已扣费、指数未扣费
   * @param benchmarkSamples 有效基准样本数
   * @param exclusions 全部排除和未成熟原因
   */
  public record Statistics(
      int windowDays,
      String status,
      int calculated,
      int attempted,
      Double meanNetReturn,
      Double positiveRatio,
      Double profitLossRatio,
      Double worstDrawdown,
      Double meanExcessReturn,
      int benchmarkSamples,
      Map<String, Integer> exclusions) {}
}
