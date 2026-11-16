package com.alphascope.portfolio.vo;

import com.alphascope.portfolio.dto.WatchlistDO;

/**
 * 自选股公开响应；不暴露watchlist或instrument表的主键。
 *
 * @author chenxuefeng
 */
public record WatchlistView(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name,

    /** 研究或排序的优先级，具体取值由该结构的业务说明确定。 */
    int priority,

    /** 是否纳入当前自选研究；不改变证券主数据。 */
    boolean enabled) {
  public static WatchlistView from(WatchlistDO row) {
    return new WatchlistView(
        row.getSymbol(), row.getName(), row.getPriority(), Boolean.TRUE.equals(row.getEnabled()));
  }
}
