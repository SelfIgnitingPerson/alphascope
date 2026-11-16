package com.alphascope.market.vo;

import com.alphascope.market.entity.MarketScore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外查询响应；与数据库实体分离，保持单位和业务时点语义。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MarketScoreView(
    /** 记录主键，自增生成 */
    Long id,

    /** 交易日期，按上海时区记录 */
    LocalDate tradeDate,

    /** 评分或观察模型标识，用于区分计算口径 */
    String modelVersion,

    /** 全市场综合评分，范围0至100；证据不足时为NULL，不能按0分解释 */
    BigDecimal score,

    /** 市场状态：UNKNOWN未知、STRONG强、POSITIVE偏强、SIDEWAYS震荡、WEAK偏弱、RISK_OFF低迷 */
    String state,

    /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
    String evidenceJson,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 记录最后更新时间，上海时区 */
    LocalDateTime updatedAt,

    /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
    Boolean isSynthetic) {
  /**
   * 显式转换已保存的市场评分及缺项证据；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 已保存的市场评分及缺项证据，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static MarketScoreView from(MarketScore row) {
    return new MarketScoreView(
        row.getId(),
        row.getTradeDate(),
        row.getModelVersion(),
        row.getScore(),
        row.getState(),
        row.getEvidenceJson(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getSynthetic());
  }
}
