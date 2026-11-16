package com.alphascope.intelligence.vo;

import java.util.List;
import lombok.Builder;

/**
 * 自选的条件式建仓、加仓或减仓研究；行情条件与个人持仓前提分开，不生成委托数量。
 *
 * @author chenxuefeng
 */
@Builder
public record PositionStrategy(
    /** 固定策略方法标识，参数与此版本一起冻结。 */
    String version,
    /** OPEN_PULLBACK、OPEN_BREAKOUT、ADD_RETEST 或 REDUCE。 */
    String code,
    /** 可读策略名称。 */
    String title,
    /** CONDITIONAL_MATCH行情条件满足、WAIT等待、WATCH复核、RISK_BLOCKED风险过滤或UNAVAILABLE资料不足。 */
    String status,
    /** ALL表示全部行情条件须满足，ANY表示任一减仓风险组合触发。 */
    String conditionLogic,
    /** 本修订策略判断及尚待核对的关键原因。 */
    String conclusion,
    /** 候选观察带，人民币元，不是成交保证或固定买入指令；减仓方案或资料不足时可为空。 */
    PriceVolumeStudy.Zone observationZone,
    /** 支撑或突破前已形成的压力证据；不能使用确认日新高重设突破门槛。 */
    PriceVolumeStudy.Zone referenceZone,
    /** 共用结构止损参考，人民币元；没有有效正数参考时为空。 */
    Double stopReference,
    /** 候选观察带上沿相对结构止损的价格距离，小数比例，不是账户亏损预算。 */
    Double referenceRisk,
    /** 新建和加仓的技术过滤上限0.08，未完成效果验证，不等于个人风险承受度。 */
    Double maximumReferenceRisk,
    /** 行情触发条件，明确分别列示观测值、要求和核验状态。 */
    List<Condition> conditions,
    /** 使该观察方案失效、需要撤销或重评的条件。 */
    List<String> invalidation,
    /** 尚未由系统核实的持仓、个人计划和资金前提。 */
    List<String> assumptions) {
  /**
   * 单条可核对行情条件；未知不可等同未触发。
   *
   * @author chenxuefeng
   */
  public record Condition(
      /** 当前核对的条件名称。 */
      String label,
      /** 已冻结的实际数值、日期或状态说明。 */
      String observed,
      /** 策略明确定义的比较要求。 */
      String required,
      /** true已满足、false未满足、null缺证据。 */
      Boolean met) {}
}
