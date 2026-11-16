package com.alphascope.market.provider;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.vo.ProviderChannelView;
import com.alphascope.operations.service.SourceLicenseService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真实来源的使用范围装饰器；本地研究不自动声称取得商用许可。
 *
 * @author chenxuefeng
 */
public final class LicensedMarketProvider implements MarketDataProvider {
  private final MarketDataProvider delegate;
  private final SourceLicenseService licenses;

  public LicensedMarketProvider(MarketDataProvider delegate, SourceLicenseService licenses) {
    this.delegate = delegate;
    this.licenses = licenses;
  }

  @Override
  public String code() {
    return delegate.code();
  }

  @Override
  public String calendarSource() {
    return delegate.calendarSource();
  }

  @Override
  public Set<MarketCapability> capabilities() {
    return delegate.capabilities();
  }

  @Override
  public boolean supportsIndexBars() {
    return delegate.supportsIndexBars();
  }

  @Override
  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    licenses.require(code(), "COLLECT");
    return delegate.fetchCalendar(from, to);
  }

  @Override
  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    licenses.require(code(), "COLLECT");
    return delegate.fetchDailyBars(symbol, from, to);
  }

  @Override
  public List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    licenses.require(code(), "COLLECT");
    return delegate.fetchIndexBars(symbol, from, to);
  }

  @Override
  public MarketWindow fetchWindow(String symbol, LocalDate from, LocalDate to, boolean index) {
    licenses.require(code(), "COLLECT");
    return delegate.fetchWindow(symbol, from, to, index);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public List<ProviderChannelView> status() {
    return delegate.status();
  }
}
