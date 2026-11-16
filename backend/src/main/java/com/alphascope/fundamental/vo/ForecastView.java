package com.alphascope.fundamental.vo;

import com.alphascope.common.vo.CollectionView;
import com.alphascope.fundamental.provider.EastmoneyForecastProvider.Estimate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 截至查询时点可见的公开盈利预测；未观测到的历史不从当前快照补填。
 *
 * @author chenxuefeng
 */
public record ForecastView(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name,

    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    String status,

    /** 截至请求时点可见的最新预测快照；尚无观测时为空。 */
    Snapshot latest,

    /** 同一业务对象的已保存版本之间的差异列表。 */
    List<Change> changes,

    /** 当前状态、判断依据或数据不足原因的文字说明。 */
    String explanation,

    /** 当前范围、数据时点和统计方法的适用边界。 */
    List<String> limitations,

    /** 采集状态及失败原因；只读页面不触发新的上游请求。 */
    CollectionView collection) {
  /**
   * 实际保存的盈利预测观测快照；首次可用时刻只采用真实观测时间。
   *
   * @author chenxuefeng
   */
  public record Snapshot(
      /** 当前资源记录的数据库主键。 */
      long id,

      /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
      LocalDateTime availableAt,

      /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
      LocalDateTime fetchedAt,

      /** 该项证据的实际供应商或原始来源标识。 */
      String source,

      /** 可核验的公开原始来源URL。 */
      String sourceUrl,

      /** 近六个月评级机构数，不等于每个年度EPS的实际参与机构数。 */
      int ratingAgencies,

      /** 按财务年度排列的公开汇总预测EPS列表。 */
      List<Estimate> estimates) {}

  /**
   * 同财务年度两份真实观测快照之间的预测EPS差额。
   *
   * @author chenxuefeng
   */
  public record Change(
      /** 预测对应的财务年度，不是快照采集年份。 */
      int year,

      /** 上一份可比同年度快照的预测EPS，单位元/股。 */
      double previousEps,

      /** 本次同年度快照的预测EPS，单位元/股。 */
      double currentEps,

      /** 本次EPS减前次EPS，单位元/股；不是百分比变化率。 */
      double difference,

      /** 上一份可比预测快照的实际首次观测时刻。 */
      LocalDateTime previousObservedAt,

      /** 当前预测快照的实际首次观测时刻。 */
      LocalDateTime currentObservedAt) {}
}
