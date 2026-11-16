package com.alphascope.market.provider;

import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.vo.ProviderProbeSample;

/**
 * 固定一日样本的契约核验；不落库、不批量回填，不将空数据当连接成功。
 *
 * @author chenxuefeng
 */
public final class ProviderProbeSamples {
  private ProviderProbeSamples() {}

  /**
   * 仅访问传入的供应商，核对实际来源及数据口径。
   *
   * @param provider 已经过授权装饰和访问保护的指定通道
   * @param request 一个基础能力与一个日期
   * @return 实际通过契约校验的样本摘要
   */
  public static ProviderProbeSample fetch(
      MarketDataProvider provider, ProviderProbeRequest request) {
    var date = request.getDate();
    var capability = request.getCapability();
    if (!provider.capabilities().contains(capability)) throw new UnsupportedOperationException();
    if (capability == MarketCapability.TRADING_CALENDAR) {
      var result = ProviderValidation.calendar(provider.fetchCalendar(date, date), date, date);
      return new ProviderProbeSample(
          null, result.size(), date, provider.calendarSource(), result.get(date));
    }
    if (capability != MarketCapability.STOCK_DAILY && capability != MarketCapability.INDEX_DAILY)
      throw new UnsupportedOperationException();
    boolean index = capability == MarketCapability.INDEX_DAILY;
    String symbol = index ? "000300.SH" : "600000.SH";
    var result =
        ProviderValidation.bars(
            index
                ? provider.fetchIndexBars(symbol, date, date)
                : provider.fetchDailyBars(symbol, date, date),
            symbol,
            date,
            date);
    if (result.stream().anyMatch(row -> !provider.acceptsSource(row.provider())))
      throw new IllegalStateException("MIXED_BATCH_SOURCE");
    return new ProviderProbeSample(
        symbol, result.size(), result.getLast().tradeDate(), result.getLast().provider(), null);
  }
}
