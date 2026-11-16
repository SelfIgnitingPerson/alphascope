package com.alphascope.industry.vo;

import com.alphascope.industry.entity.ThemeScore;
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
public record ThemeScoreView(
    /** 记录主键，自增生成 */
    Long id,

    /** 主题主键，关联theme.id */
    Long themeId,

    /** 交易日期，按上海时区记录 */
    LocalDate tradeDate,

    /** 评分或观察模型标识，用于区分计算口径 */
    String modelVersion,

    /** 主题强弱评分，范围0至100；覆盖不足或演示模式时为空 */
    BigDecimal score,

    /** 有效成员20期相对沪深300强度的等权均值，小数存储 */
    BigDecimal rs20,

    /** 有效成员中收盘价高于MA20的比例，范围0至1；无有效成员时为空 */
    BigDecimal breadth,

    /** 有效计算成员数除以当日完整成员数，范围0至1 */
    BigDecimal coverage,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 记录最后更新时间，上海时区 */
    LocalDateTime updatedAt,

    /** 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 */
    String evidenceJson,

    /** 历史有效成员当日同源复权收益的等权均值，小数存储 */
    BigDecimal dayReturn,

    /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
    Boolean isSynthetic) {
  /**
   * 显式转换已保存的主题评分及覆盖证据；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 已保存的主题评分及覆盖证据，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static ThemeScoreView from(ThemeScore row) {
    return new ThemeScoreView(
        row.getId(),
        row.getThemeId(),
        row.getTradeDate(),
        row.getModelVersion(),
        row.getScore(),
        row.getRs20(),
        row.getBreadth(),
        row.getCoverage(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getEvidenceJson(),
        row.getDayReturn(),
        row.getSynthetic());
  }
}
