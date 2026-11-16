package com.alphascope.intelligence.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 近两个月量价、价格区间和条件式减仓研究；仅解释冻结行情，不推断个人成本或委托成交。
 *
 * @author chenxuefeng
 */
@Builder(toBuilder = true)
public record PriceVolumeStudy(
    /** 方法标识，全部阈值与该版本绑定。 */
    String version,
    /** 统一证券代码。 */
    String symbol,
    /** 证券名称。 */
    String name,
    /** AVAILABLE、INSUFFICIENT_DATA、STALE、SUSPENDED 或 DEMO。 */
    String status,
    /** 请求窗口起点，截止日减两个自然月，含当日。 */
    LocalDate windowStart,
    /** 业务截止日；任何未来行情均不参与计算。 */
    LocalDate asOf,
    /** 窗口内实际有效K线数，包含截止日。 */
    int observedDays,
    /** 扣除同源明确停牌后的应有交易日数；未保存日历时为0。 */
    int expectedDays,
    /** 有效K线的最早日期；无记录时为空。 */
    LocalDate actualStart,
    /** 单一行情和因子来源；证据不一致时为空。 */
    String source,
    /** 截止日未复权收盘价，人民币元。 */
    Double close,
    /** 最近5个交易日同锚点复权收益，小数比例。 */
    Double return5,
    /** 当日量能 / 前20个有效交易日均量，具体口径由volumeBasis声明；缺失时为空。 */
    Double volumeRatio,
    /** 最近5日均量能 / 此前独立20日均量能；两窗口不重叠。 */
    Double volumeRatio5,
    /** 截止日前14期平均真实波幅，人民币元；不包含截止日。 */
    Double atr14,
    /** 含截止日的20期同锚点复权收盘均价，人民币元。 */
    Double ma20,
    /** 原结构支撑区间，可高于当前价格，此时表示已失守，不能改作下方支撑。 */
    Zone support,
    /** 原支撑下沿减0.5倍ATR14，人民币元；不是保证成交价，已跌破时不向下重置。 */
    Double stopReference,
    /** ABOVE_REFERENCE 尚未触及、BREACHED 已触及/跌破、UNAVAILABLE 不足。 */
    String stopStatus,
    /** 最多3个上方压力候选，按距离排序；突破两月样本后可能为空。 */
    List<Zone> resistances,
    /** REDUCE_REVIEW、PROFIT_PROTECTION_REVIEW、WATCH_REDUCTION、OBSERVE 或 UNAVAILABLE。 */
    String decision,
    /** 当前证据支持的条件式减仓意见；未接入个人成本、资金需求或持仓比例。 */
    String conclusion,
    /** 参与判断的已观测指标、比较结果和量价解释。 */
    List<String> evidence,
    /** 缺失日历、日期、OHLC、同源因子等具体原因。 */
    List<String> missing,
    /** 方法适用范围、参数与执行限制；不宣称具备预测效果。 */
    List<String> methodNotes,
    /** TURNOVER_AMOUNT表示成交额量能；旧V1为空，保留其冻结的等效量代理解释。 */
    @JsonInclude(JsonInclude.Include.NON_NULL) String volumeBasis,
    /** 新修订冻结的条件式建仓、加仓和减仓方案；旧修订为空，不补算新策略。 */
    @JsonInclude(JsonInclude.Include.NON_NULL) List<PositionStrategy> strategies) {
  /**
   * 价格区间及其可追溯来源；日K线成交分布为近似，不是逐笔筹码成本。
   *
   * @author chenxuefeng
   */
  public record Zone(
      /** 区间下沿，当前锚点复权人民币元。 */
      double lower,
      /** 区间上沿，当前锚点复权人民币元。 */
      double upper,
      /** CONFIRMED_SWING_HIGH、TWO_MONTH_HIGH、DAILY_VOLUME_ZONE、RECENT_SWING_LOW 或 PRIOR_20_LOW。 */
      String basis,
      /** 区间对应的独立证据交易日期。 */
      List<LocalDate> dates,
      /** 仅DAILY_VOLUME_ZONE有效；该日K近似价桶量能占此前窗口总量的小数比例。 */
      Double volumeShare) {}
}
