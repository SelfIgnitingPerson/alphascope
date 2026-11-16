package com.alphascope.backtest.vo;

import com.alphascope.backtest.dto.AvailabilityMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 已保存信号的描述统计和时间留出验证，不模拟下单或可成交性。
 *
 * @author chenxuefeng
 */
public record BacktestView(
    /** OBSERVED表示确有成熟样本；无有效样本时保持不足或未触发。 */
    String dataStatus,
    /** 无法统计或样本不足的具体原因。 */
    String reason,
    /** 按5D/10D/20D和规则代码索引的描述统计。 */
    Map<String, Map<String, SignalStatisticView>> statistics,
    /** 成本、时点及可成交性的方法边界。 */
    List<String> limitations,
    /** 实际可用性检查模式；回顾统计不证明历史时点可得。 */
    AvailabilityMode availabilityMode,
    /** 统计使用的收益截止日期，不使用其后的行情。 */
    LocalDate evaluationDate,
    /** 较晚留出区间首日，不根据收益好坏反复移动。 */
    LocalDate splitDate,
    /** EARLY和HOLDOUT区间的独立统计。 */
    List<ValidationWindow> validationWindows,
    /** 小样本提示阈值，仅为诊断，不是方法有效性的充分条件。 */
    int minimumSamples,
    /** 当前历史验证方法标识，不覆盖旧报告算法或冻结输入。 */
    String methodVersion) {
  /**
   * 保留无数据和演示拒绝响应的构造入口。
   *
   * @param dataStatus 统计状态
   * @param reason 不可用原因
   * @param statistics 已有统计或空字典
   * @param limitations 方法边界
   */
  public BacktestView(
      String dataStatus,
      String reason,
      Map<String, Map<String, SignalStatisticView>> statistics,
      List<String> limitations) {
    this(
        dataStatus,
        reason,
        statistics,
        limitations,
        AvailabilityMode.RETROSPECTIVE,
        null,
        null,
        List.of(),
        20,
        "SAVED_SIGNAL_VALIDATION_V3");
  }
}
