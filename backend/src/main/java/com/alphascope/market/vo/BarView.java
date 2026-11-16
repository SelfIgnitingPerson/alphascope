package com.alphascope.market.vo;

import com.alphascope.market.dto.MarketBarDO;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公开日线响应；原价和同源复权因子分别返回，供调用方采用统一分析锚点。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BarView(
    /** 行情或指标所属交易日期，使用上海时区。 */
    LocalDate tradeDate,

    /** 未复权开盘价，单位人民币元。 */
    BigDecimal open,

    /** 未复权最高价，单位人民币元。 */
    BigDecimal high,

    /** 未复权最低价，单位人民币元。 */
    BigDecimal low,

    /** 未复权收盘价，单位人民币元；缺失时不能当作0。 */
    BigDecimal close,

    /** 成交量，单位股；供应商使用手时必须先换算。 */
    BigDecimal volume,

    /** 成交金额，单位人民币元；缺失时不补造0。 */
    BigDecimal amount,

    /** 与原价同源的正数复权因子；缺失时不能假定为1。 */
    BigDecimal factor,

    /** 该项证据的实际供应商或原始来源标识。 */
    String source,

    /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
    LocalDateTime fetchedAt,

    /** 原始行情写入批次，用于追溯本条事实。 */
    String batchId,

    /** 是否为演示数据；真实数据和演示数据必须隔离。 */
    boolean isSynthetic) {
  /**
   * 显式转换查询得到的同源日线及复权因子；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 查询得到的同源日线及复权因子，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static BarView from(MarketBarDO row) {
    return new BarView(
        row.getTradeDate(),
        row.getOpen(),
        row.getHigh(),
        row.getLow(),
        row.getClose(),
        row.getVolume(),
        row.getAmount(),
        row.getFactor(),
        row.getSource(),
        row.getFetchedAt(),
        row.getBatchId(),
        Boolean.TRUE.equals(row.getSynthetic()));
  }
}
