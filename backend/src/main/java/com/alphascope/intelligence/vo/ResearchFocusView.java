package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 自选研究的当日优先事项；只比较已保存版本，不构成交易或历史可得性证明。
 *
 * @author chenxuefeng
 * @param asOf 请求的研究日
 * @param items 本页核对结果，按风险与变化排序
 * @param total 启用自选与已登记持仓的去重总数；不能把本页当作全量
 * @param page 当前页码
 * @param size 按自选优先级划分的每页数量
 */
public record ResearchFocusView(
    LocalDate asOf,
    List<Item> items,
    int total,
    int page,
    int size,
    /** 全量已登记持仓核对，不受本页或筛选截断。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        PortfolioImpact portfolioImpact,
    /** 局部资料读取失败时保留可操作缺项，不遮住其他标的。 */
    List<String> issues,
    /** 当前决策可使用今日登记；历史回看严格限制在研究日，当日与资料视角分别展示。 */
    LocalDate knowledgeDate,
    /** 当前是否允许持仓进入风险排序。 */
    boolean positionRiskEnabled) {
  /**
   * 已登记组合的局部敞口，不声称覆盖券商全部资产或行业关联风险。
   *
   * @author chenxuefeng
   * @param heldCount 已登记正持仓数
   * @param pendingCount 缺少同日持仓或行情的数量
   * @param knownMarketValue 已成功核对的持仓市值；无有效样本为null
   * @param knownWeight 已知市值相对同日登记投资资产的比例；不是完整股票仓位
   */
  public record PortfolioImpact(
      int heldCount,
      int pendingCount,
      Double knownMarketValue,
      Double knownWeight,
      Double cashBalance,
      Double cashAfterReserve,
      Double stress10Loss,
      Double stress20Loss,
      String referenceBudgetStatus,
      List<RiskGroup> riskGroups,
      List<String> limitations) {}

  /**
   * 人工相关风险分组，可重叠，不能把各组相加当成组合总仓位。
   *
   * @author chenxuefeng
   * @param label 人工风险标签或未分类
   * @param marketValue 已核验或明确估算的市值
   * @param weight 相对登记投资资产比例，未知留空
   */
  public record RiskGroup(String label, double marketValue, Double weight) {}

  /**
   * 单只证券的一项首要研究事项，其他周期可从个股页继续核对。
   *
   * @author chenxuefeng
   * @param symbol 统一代码
   * @param name 证券名称
   * @param category RISK、MATCH、DATA_GAP、CHANGED或WAIT
   * @param title 可直接理解的事项标题
   * @param explanation 具体周期、方案及对照限制
   * @param horizon 首要事项的周期，资料缺失时为空
   * @param quoteDate 实际行情日，不用请求日替换旧行情
   * @param comparisonDate 已尝试核对的上一证券交易日；日历缺失时为空
   * @param newlyMatched 是否已用同方法、同源且完整上期对照确认条件新满足
   * @param changeLabel 与上期的可比变化，不把持续信号称为新增
   * @param priorities 首项为当前核对重点，其他项保留不同周期的风险与观察依据
   */
  @Builder(toBuilder = true)
  public record Item(
      String symbol,
      String name,
      String category,
      String title,
      String explanation,
      String horizon,
      LocalDate quoteDate,
      LocalDate comparisonDate,
      boolean newlyMatched,
      String changeLabel,
      List<ResearchPriority> priorities,
      /** 新增、持续、恢复、口径变化或不可比，不作为买入信号。 */
      String changeState,
      /** 当前账号同日人工持仓影响。 */
      PositionImpact positionImpact,
      /** 实际两期输入来源，组合派生数值沿用同一来源许可。 */
      java.util.Set<String> inputSources,
      /** FROZEN_DAILY实际留档或CURRENT_RULE_REPLAY当前规则复算。 */
      String comparisonBasis,
      /** 实际上期判断的形成时刻。 */
      java.time.LocalDateTime previousCapturedAt,
      /** 原留档的主要判断。 */
      String previousTitle,
      /** 存在原方案触及记录时的详情入口。 */
      Long originalPlanId) {}
}
