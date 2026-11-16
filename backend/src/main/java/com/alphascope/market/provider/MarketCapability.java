package com.alphascope.market.provider;

/**
 * 独立声明供应商能力，支持某项能力不等于已取得账号权限或网络可用。
 *
 * @author chenxuefeng
 */
public enum MarketCapability {
  /** 供应商明确开休市日历。 */
  TRADING_CALENDAR,
  /** 股票原价与同源因子日线。 */
  STOCK_DAILY,
  /** 满足项目量额口径的指数日线。 */
  INDEX_DAILY,
  /** 历史沪深市场证券清单和同日未复权截面。 */
  MARKET_SNAPSHOT,
  /** 可核验覆盖范围的复权事件或每日因子。 */
  ADJUSTMENT_FACTORS
}
