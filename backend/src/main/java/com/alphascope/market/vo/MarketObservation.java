package com.alphascope.market.vo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 独立沪深观察模型，不把分项结果放大为完整全A五维总分。比例为小数，金额为元。
 *
 * @author chenxuefeng
 */
public record MarketObservation(
    /** 独立沪深分项观察模型标识；不代表完整全A评级。 */
    String version,

    /** 本次研究采用的业务日期，使用上海时区。 */
    LocalDate businessDate,

    /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
    String scope,

    /** 分项整体可用状态，覆盖不足时保留PARTIAL或INSUFFICIENT_DATA等状态。 */
    String status,

    /** 沪深300的价格、均线及趋势判断分项。 */
    Trend trend,

    /** 沪深成交额相对历史均额的量能分项，缺失历史时保留不足。 */
    Turnover turnover,

    /** 沪深交易股票的均线广度、有效分母及覆盖率分项。 */
    Breadth breadth,

    /** 本次计算实际采用的阈值参数，须随冻结证据一起保存。 */
    Map<String, Double> parameters,

    /** 当前范围、数据时点和统计方法的适用边界。 */
    List<String> limitations,
    /** 仅新三所观察附实际输入来源；旧冻结内容缺省不改写。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Set<String> inputSources) {
  /** 兼容既有冻结证据格式，旧输入不猜测来源。 */
  public MarketObservation(
      String version,
      LocalDate businessDate,
      String scope,
      String status,
      Trend trend,
      Turnover turnover,
      Breadth breadth,
      Map<String, Double> parameters,
      List<String> limitations) {
    this(
        version,
        businessDate,
        scope,
        status,
        trend,
        turnover,
        breadth,
        parameters,
        limitations,
        null);
  }

  /**
   * 沪深300价格与20/60日均线的趋势事实，不给出固定交易价格。
   *
   * @author chenxuefeng
   */
  public record Trend(
      /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
      String status,

      /** 分项中文判断；证据不足时明确保留不足状态。 */
      String judgment,

      /** 沪深300当日未复权收盘价；用于独立指数趋势观测。 */
      Double close,

      /** 沪深300最近20个交易日收盘均价；历史不足时为空。 */
      Double ma20,

      /** 沪深300最近60个交易日收盘均价；历史不足时为空。 */
      Double ma60,

      /** 当前MA20减前一交易日同锚点MA20，单位元；不是百分比斜率。 */
      Double ma20Slope,

      /** 相邻两个有效收盘的同源复权收益，以小数存储。 */
      Double dayReturn,

      /** 当前状态、判断依据或数据不足原因的文字说明。 */
      String explanation) {}

  /**
   * 沪深成交额相对5/20日均额的量能观察，要求连续20个实际交易日完整。
   *
   * @author chenxuefeng
   */
  public record Turnover(
      /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
      String status,

      /** 分项中文判断；证据不足时明确保留不足状态。 */
      String judgment,

      /** 成交金额，单位人民币元；缺失时不补造0。 */
      Double amount,

      /** 包含业务日的最近5个实际交易日成交额均值，单位元；必须满足完整20日窗口。 */
      Double average5,

      /** 包含业务日的最近20个实际交易日成交额均值，单位元；窗口不完整时为空。 */
      Double average20,

      /** 当日成交额除以5日成交额均值，单位倍；该均值包含业务日。 */
      Double ratio5,

      /** 当日成交额除以包含业务日的20日均额，单位倍；历史不完整时为空。 */
      Double ratio20,

      /** 最近20个实际交易日窗口内取得完整市场截面的天数。 */
      int completeDays,

      /** 当前状态、判断依据或数据不足原因的文字说明。 */
      String explanation) {}

  /**
   * 沪深交易股票的MA20/MA60均线广度、有效样本分母与覆盖率。
   *
   * @author chenxuefeng
   */
  public record Breadth(
      /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
      String status,

      /** 分项中文判断；证据不足时明确保留不足状态。 */
      String judgment,

      /** 当前市场截面中取得有效日线的交易股票家数，不含明确停牌。 */
      int trading,

      /** 当前截面中供应商明确标记停牌的股票家数。 */
      int suspended,

      /** 具备至少20个有效复权收盘的当日交易股票数。 */
      int valid20,

      /** 有效MA20样本中当前收盘高于MA20的股票数。 */
      int above20,

      /** 有效MA20样本数除以当日交易股票数，范围0至1。 */
      Double coverage20,

      /** 有效MA20样本中收盘高于MA20的比例；覆盖不足时为空。 */
      Double ratio20,

      /** 具备至少60个有效复权收盘的当日交易股票数。 */
      int valid60,

      /** 有效MA60样本中当前收盘高于MA60的股票数。 */
      int above60,

      /** 有效MA60样本数除以当日交易股票数，范围0至1。 */
      Double coverage60,

      /** 有效MA60样本中收盘高于MA60的比例，以0至1小数表示；覆盖不足时为空。 */
      Double ratio60,

      /** 上涨家数除以取得有效日线的交易股票数，范围0至1。 */
      Double advancingRatio,

      /** 当前状态、判断依据或数据不足原因的文字说明。 */
      String explanation) {}
}
