package com.alphascope.signal.vo;

import com.alphascope.signal.entity.SignalEvent;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外查询响应；与数据库实体分离，保持单位和业务时点语义。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SignalView(
    /** 记录主键，自增生成 */
    Long id,

    /** 标的主键，关联instrument.id */
    Long instrumentId,

    /** 交易日期，按上海时区记录 */
    LocalDate tradeDate,

    /** 风险规则代码，如TREND_BREAK_20、PROFIT_SLOWDOWN */
    String ruleCode,

    /** 规则及阈值配置标识，用于追溯判断口径 */
    String ruleVersion,

    /** 风险严重程度：HIGH高、MEDIUM中、LOW低 */
    String severity,

    /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
    String evidenceJson,

    /** 评估状态：TRIGGERED触发、NOT_TRIGGERED未触发、INSUFFICIENT_DATA不足、NOT_APPLICABLE不适用；演示见is_synthetic */
    String status,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 记录最后更新时间，上海时区 */
    LocalDateTime updatedAt,

    /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
    Boolean isSynthetic) {
  /**
   * 显式转换已保存的规则评估及原始证据；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 已保存的规则评估及原始证据，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static SignalView from(SignalEvent row) {
    return new SignalView(
        row.getId(),
        row.getInstrumentId(),
        row.getTradeDate(),
        row.getRuleCode(),
        row.getRuleVersion(),
        row.getSeverity(),
        row.getEvidenceJson(),
        row.getStatus(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getSynthetic());
  }
}
