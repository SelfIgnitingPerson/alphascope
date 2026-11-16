package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 经典收益统计；量子纠缠仅作用户指定的类比，不是物理测量或已验证预测模型。
 *
 * @author chenxuefeng
 */
public record ThemeResearch(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 主题稳定代码，例如PCB或COOLING。 */
    String themeCode,

    /** 供研究页面展示的中文标题。 */
    String title,

    /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
    String scope,

    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    String status,

    /** 该项证据的实际供应商或原始来源标识。 */
    Source source,

    /** 该不可变研究样本定义的完整成员，不等于整个行业成分。 */
    List<Member> members,

    /** 同一研究样本最近三个实际交易日的收益、广度及覆盖记录。 */
    List<Day> recentDays,

    /** 排除当前标的自身后的20期同业联动统计。 */
    Window window20,

    /** 排除当前标的自身后的60期同业联动统计。 */
    Window window60,

    /** 从当前样本实际数据得到的观测事实。 */
    String observation,

    /** 待验证的解释假设；不能当作已经证实的事实。 */
    List<String> hypotheses,

    /** 后续需要人工核对的事项列表。 */
    List<String> checks,

    /** 当前范围、数据时点和统计方法的适用边界。 */
    List<String> limitations) {
  /**
   * 该研究样本采用的原始公开来源及其边界。
   *
   * @author chenxuefeng
   */
  public record Source(
      /** 供研究页面展示的中文标题。 */
      String title,

      /** 原始公开材料链接，用于核查证据。 */
      String url,

      /** 原始材料中支持该样本定义的页码。 */
      String pages,

      /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
      String availableAt,

      /** 原始内容的SHA-256指纹，用于去重和追溯。 */
      String contentHash,

      /** 固定研究样本的选择依据、成员定义及覆盖边界。 */
      String definition) {}

  /**
   * 固定研究样本的一名完整登记成员，不自动加入自选。
   *
   * @author chenxuefeng
   */
  public record Member(
      /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
      String symbol,

      /** 标的中文名称，用于页面展示。 */
      String name) {}

  /**
   * 同一研究样本某个实际交易日的等权收益与覆盖证据。
   *
   * @author chenxuefeng
   */
  public record Day(
      /** 该记录对应的业务交易日期。 */
      LocalDate date,

      /** 相邻两个有效收盘的同源复权收益，以小数存储。 */
      Double dayReturn,

      /** 有效成员中收盘高于MA20的比例，范围0至1。 */
      Double breadth,

      /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
      Double coverage,

      /** 该日期满足计算条件的成员数量。 */
      int validMembers,

      /** 该业务日期研究样本的完整成员数量。 */
      int totalMembers) {}

  /**
   * 剔除当前标的自身后的同业窗口统计；相关性不证明因果或预测能力。
   *
   * @author chenxuefeng
   */
  public record Window(
      /** 统计回看交易期数，当前为20或60。 */
      int lookback,

      /** 实际用于相关性计算的日期对齐收益样本数。 */
      int samples,

      /** 剔除当前标的自身后应覆盖的同业成员数量。 */
      int expectedPeers,

      /** 实际取得完整窗口并参与计算的同业代码列表。 */
      List<String> peers,

      /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
      Double coverage,

      /** 个股与留一法同业日收益的皮尔逊相关系数，范围-1至1。 */
      Double correlation,

      /** 分别剔除沪深300线性影响后，个股与同业收益的偏相关系数。 */
      Double marketAdjustedCorrelation,

      /** 当前标的在该统计窗口的累计复权收益，以小数存储。 */
      Double stockReturn,

      /** 窗口内同业每日等权组合的累计收益，以小数存储。 */
      Double peerReturn,

      /** 标的窗口收益减同业组合窗口收益；乘100后为百分点差。 */
      Double excessReturn,

      /** 统计窗口最后一个交易日的同业等权收益，以小数存储。 */
      Double peerDayReturn,

      /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
      String status) {}
}
