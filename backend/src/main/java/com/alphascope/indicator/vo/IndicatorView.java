package com.alphascope.indicator.vo;

import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 按同源复权口径计算的技术指标响应；所有不足值保留为空。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IndicatorView(
    /** 行情或指标所属交易日期，使用上海时区。 */
    LocalDate tradeDate,

    /** 本次计算采用的算法标识，用于核对历史口径。 */
    String algorithmVersion,

    /** 最近5个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
    BigDecimal ma5,

    /** 最近10个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
    BigDecimal ma10,

    /** 最近20个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
    BigDecimal ma20,

    /** 最近60个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
    BigDecimal ma60,

    /** 14期Wilder平均真实波幅，单位人民币元；历史不足时为空。 */
    BigDecimal atr14,

    /** 20期标的收益比除以同期沪深300收益比再减1，以小数存储；日期不齐时为空。 */
    BigDecimal rs20,

    /** 当日成交量除以前20个有K线交易日均量，单位倍；均量不含当日。 */
    BigDecimal volRatio,

    /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
    String dataStatus,

    /** 是否为演示数据；真实数据和演示数据必须隔离。 */
    boolean isSynthetic,

    /** 计算所采用完整输入的SHA-256指纹。 */
    String inputHash,

    /** 输入序列最后一条行情的原始采集批次。 */
    String sourceBatch) {
  /**
   * 显式转换已保存的指标及计算口径；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 已保存的指标及计算口径，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static IndicatorView from(IndicatorSnapshot row) {
    return new IndicatorView(
        row.getTradeDate(),
        row.getAlgorithmVersion(),
        row.getMa5(),
        row.getMa10(),
        row.getMa20(),
        row.getMa60(),
        row.getAtr14(),
        row.getRs20(),
        row.getVolRatio(),
        row.getDataStatus(),
        Boolean.TRUE.equals(row.getSynthetic()),
        row.getInputHash(),
        row.getSourceBatch());
  }
}
