package com.alphascope.market.service.impl;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.diagnostic.baostock.BaoStockErrors;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.market.dto.InstrumentSearchRequest;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.MarketBarQuery;
import com.alphascope.market.dto.MarketCacheSettings;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.dto.SuspensionQuery;
import com.alphascope.market.dto.TradingCalendarWriteCommand;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.policy.MarketCachePolicy;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.baostock.BaoStockRequestPolicy;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketWriteService;
import com.alphascope.market.vo.BaoStockPolicyView;
import com.alphascope.market.vo.CapabilityView;
import com.alphascope.market.vo.InstrumentView;
import com.alphascope.market.vo.MarketAcquisition;
import com.alphascope.market.vo.ProviderChannelView;
import com.alphascope.market.vo.ProviderStatusView;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * MarketService 的业务实现。
 *
 * <p>行情查询与获取编排：校验同源、模式和完整交易日，区分新采集、有效缓存与已存历史。
 *
 * @author chenxuefeng
 */
@Service
public class MarketServiceImpl implements MarketService {
  private final MarketMapper mapper;
  private final MarketDataProvider provider;
  private final MarketWriteService writer;
  private final ResearchCollectionMapper collections;
  private final ResearchDataRouter research;

  private long cacheMinutes;
  private final int baoStockDailyBudget;
  private final List<AlphaScopeProperties.ProviderType> requestedChannels;

  public MarketServiceImpl(
      MarketMapper mapper,
      MarketDataProvider provider,
      MarketWriteService writer,
      ResearchCollectionMapper collections,
      ResearchDataRouter research,
      AlphaScopeProperties properties) {
    this.mapper = mapper;
    this.provider = provider;
    this.writer = writer;
    this.collections = collections;
    this.research = research;
    this.baoStockDailyBudget = properties.getMarket().getBaostockDailyBudget();
    this.cacheMinutes = properties.getMarket().getCacheMinutes();
    this.requestedChannels =
        properties.getProvider() == AlphaScopeProperties.ProviderType.MULTI
            ? List.copyOf(properties.getMarket().getChannels())
            : List.of(properties.getProvider());
  }

  /** {@inheritDoc} */
  @Override
  public boolean synthetic() {
    return provider.synthetic();
  }

  /** {@inheritDoc} */
  @Override
  public String dataScope() {
    return provider.scope();
  }

  /** {@inheritDoc} */
  @Override
  public ProviderStatusView providerStatus() {
    var channels = provider.status().stream().map(this::withCapabilities).toList();
    var inactive = new ArrayList<ProviderStatusView.InactiveChannel>();
    if (!provider.synthetic()) {
      for (var type :
          List.of(
              AlphaScopeProperties.ProviderType.EXCHANGE_CALENDAR,
              AlphaScopeProperties.ProviderType.BAOSTOCK,
              AlphaScopeProperties.ProviderType.SINA,
              AlphaScopeProperties.ProviderType.EASTMONEY,
              AlphaScopeProperties.ProviderType.TUSHARE)) {
        if (channels.stream().anyMatch(channel -> channel.channel().equals(type.name()))) continue;
        boolean missingToken =
            type == AlphaScopeProperties.ProviderType.TUSHARE && requestedChannels.contains(type);
        inactive.add(
            new ProviderStatusView.InactiveChannel(
                type.name(),
                missingToken ? "NOT_CONFIGURED" : "DISABLED",
                missingToken
                    ? "已列入组合，但未配置TUSHARE_TOKEN，启动时跳过；未请求上游，不能判定账号权限。"
                    : "未列入当前运行的行情路由，没有发起请求；这不是供应商拒绝访问。",
                missingToken
                    ? "需要该来源时在本机配置Token并重启服务，再分别核验接口权限；免费组合仍可继续使用。"
                    : "确有需要时调整MARKET_PROVIDER / MARKET_CHANNELS并重启，不必为消除未启用状态开通付费服务。"));
      }
    }
    return new ProviderStatusView(
        provider.code(),
        provider.scope(),
        provider.synthetic(),
        provider.calendarSource(),
        channels,
        mapper.latestSources(provider.synthetic()),
        List.copyOf(inactive));
  }

  /** {@inheritDoc} */
  @Override
  public BaoStockPolicyView baoStockPolicy() {
    int limit = baoStockDailyBudget;
    try {
      var usage = BaoStockRequestPolicy.standard(limit).usage();
      var access = BaoStockAccessGuard.standard().state();
      return (new BaoStockPolicyView(
          usage.date(),
          limit,
          usage.requests(),
          Math.max(0, limit - usage.requests()),
          access.blocked(),
          null,
          access.code(),
          access.observedAt(),
          BaoStockErrors.evidence(access.code(), access.errorMessage())));
    } catch (IllegalStateException e) {
      return (new BaoStockPolicyView(
          null, limit, null, null, null, e.getMessage(), null, null, null));
    }
  }

  private ProviderChannelView withCapabilities(ProviderChannelView channel) {
    var capabilities = new ArrayList<CapabilityView>();
    if (!channel.capabilities().isEmpty()) {
      capabilities.addAll(channel.capabilities());
    } else {
      for (var capability :
          List.of(
              MarketCapability.TRADING_CALENDAR,
              MarketCapability.STOCK_DAILY,
              MarketCapability.INDEX_DAILY)) {
        boolean supported =
            capability != MarketCapability.INDEX_DAILY
                || Boolean.TRUE.equals(channel.supportsIndexBars());
        capabilities.add(
            new CapabilityView(
                capability,
                supported,
                supported ? channel.state() : "UNSUPPORTED",
                channel.lastSuccess(),
                channel.lastError(),
                channel.retryAt(),
                null,
                channel.diagnostic()));
      }
    }
    capabilities.addAll(research.status(channel.channel()));
    return new ProviderChannelView(
        channel.channel(),
        channel.supportsIndexBars(),
        channel.state(),
        channel.consecutiveFailures(),
        channel.lastSuccess(),
        channel.lastError(),
        channel.retryAt(),
        channel.isSynthetic(),
        capabilities);
  }

  /** {@inheritDoc} */
  @Override
  public String provider() {
    return provider.code();
  }

  /** {@inheritDoc} */
  @Override
  public MarketCacheSettings cachePolicy() {
    return new MarketCacheSettings(cacheMinutes, "VERIFIED_STORED_V1");
  }

  /** {@inheritDoc} */
  @Override
  public MarketAcquisition acquisition(String symbol, LocalDate date) {
    var row = collections.collection("MARKET_WINDOW", symbol, date);
    return row == null ? null : Jsons.read(row.getEvidenceJson(), MarketAcquisition.class);
  }

  private void acquired(
      String symbol, String mode, LocalDate start, LocalDate end, List<MarketBarDO> rows) {
    var times =
        rows.stream().map(MarketBarDO::getFetchedAt).filter(Objects::nonNull).sorted().toList();
    var evidence =
        new MarketAcquisition(
            symbol,
            mode,
            rows.isEmpty() ? "UNKNOWN" : rows.getFirst().getSource(),
            start,
            end,
            rows.size(),
            times.isEmpty() ? null : times.getFirst(),
            times.isEmpty() ? null : times.getLast(),
            acquisitionNote(mode));
    collections.collectionSave(
        ResearchCollection.builder()
            .dataKind("MARKET_WINDOW")
            .scope(symbol)
            .businessDate(end)
            .status(mode)
            .evidenceJson(Jsons.write(evidence))
            .build());
  }

  /** 获取方式只说明本次证据如何取得，不把旧缓存描述成重新采集。 */
  private String acquisitionNote(String mode) {
    return switch (mode) {
      case "STORED_HISTORY" -> "历史补跑使用已保存真实行情版本，未联网复核供应商最新修订；原始采集时间和批次保持不变。";
      case "FRESH_CACHE" -> "复用有效期内的完整同源缓存，原始采集时间保持不变。";
      default -> "本次从真实通道取得完整行情，保留实际来源与采集时刻。";
    };
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<InstrumentView> instruments(InstrumentSearchRequest request) {
    String q = request.getQ();
    int page = request.getPage();
    int size = request.getSize();

    request.validate();
    var items =
        mapper.instruments(q, size, (page - 1) * size).stream().map(InstrumentView::from).toList();
    return new PageResponse<>(items, mapper.instrumentCount(q), page, size);
  }

  /** {@inheritDoc} */
  @Override
  public Instrument instrument(String symbol) {
    var i = mapper.instrument(symbol);
    if (i == null) {
      throw new NotFoundException("标的不存在");
    }
    return i;
  }

  /** {@inheritDoc} */
  @Override
  public List<MarketBarDO> bars(String symbol, DateRangeRequest request) {
    LocalDate start = request.getStart();
    LocalDate end = request.getEnd();
    DateRangeValidator.requireValid(start, end);
    if (start.plusYears(10).isBefore(end)) {
      throw new IllegalArgumentException("行情查询窗口不能超过十年");
    }
    return readBars(symbol, request);
  }

  /** {@inheritDoc} */
  @Override
  public List<MarketBarDO> researchBars(String symbol, DateRangeRequest request) {
    DateRangeValidator.requireValid(request.getStart(), request.getEnd());
    if (request.getStart().plusYears(10).isBefore(request.getEnd())) {
      throw new IllegalArgumentException("研究区间不能超过十年，预热另行计算");
    }
    return readBars(
        symbol,
        DateRangeRequest.builder()
            .start(request.getStart().minusMonths(4))
            .end(request.getEnd())
            .build());
  }

  private List<MarketBarDO> readBars(String symbol, DateRangeRequest request) {
    var selected = instrument(symbol);
    LocalDate start = request.getStart();
    LocalDate end = request.getEnd();
    String source = mapper.latestSourceAt(selected.getId(), provider.synthetic(), end);
    if (source == null || !provider.acceptsSource(source)) {
      return List.of();
    }
    return mapper.bars(
        MarketBarQuery.builder()
            .symbol(symbol)
            .startDate(start)
            .endDate(end)
            .synthetic(provider.synthetic())
            .source(source)
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOpen(LocalDate date) {
    Boolean open = mapper.isOpen(date, provider.synthetic());
    if (open == null) {
      throw new IllegalStateException("交易日历缺失");
    }
    return open;
  }

  /** {@inheritDoc} */
  @Override
  public LocalDate lastOpen(LocalDate start, LocalDate end) {
    return mapper.lastOpen(start, end, provider.synthetic());
  }

  /** {@inheritDoc} */
  @Override
  public void syncCalendar(LocalDate start, LocalDate end) {
    // 已结束日期的完整、按模式隔离的供应商日历可复用；远端故障不抹掉已有休市证据。
    if (end.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")))
        && mapper.calendarDays(start, end, synthetic())
            == ChronoUnit.DAYS.between(start, end) + 1) {
      return;
    }
    provider
        .fetchCalendar(start, end)
        .forEach(
            (d, o) ->
                mapper.calendar(
                    TradingCalendarWriteCommand.builder()
                        .tradeDate(d)
                        .open(o)
                        .synthetic(provider.synthetic())
                        .source(provider.calendarSource())
                        .build()));
  }

  /** {@inheritDoc} */
  @Override
  public Set<LocalDate> expectedDates(TradingDatesQuery query) {
    String symbol = query.symbol();
    LocalDate start = query.start();
    LocalDate end = query.end();
    String source = query.source();
    DateRangeValidator.requireValid(start, end);
    var i = instrument(symbol);
    var listed = mapper.listedAt(i.getId());
    if (listed != null && listed.isAfter(start)) {
      start = listed;
    }
    if (start.isAfter(end)) {
      return Set.of();
    }
    if (mapper.calendarDays(start, end, synthetic()) != ChronoUnit.DAYS.between(start, end) + 1) {
      throw new IllegalStateException("交易日历区间不完整");
    }
    var expected = new TreeSet<>(mapper.openDates(start, end, synthetic()));
    if (source != null) {
      expected.removeAll(
          mapper.suspensions(
              SuspensionQuery.builder()
                  .instrumentId(i.getId())
                  .source(source)
                  .synthetic(synthetic())
                  .startDate(start)
                  .endDate(end)
                  .build()));
    }
    return expected;
  }

  /** {@inheritDoc} */
  @Override
  public String dayStatus(String symbol, LocalDate date) {
    var listed = mapper.listedAt(instrument(symbol).getId());
    if (listed != null && date.isBefore(listed)) {
      return "NOT_LISTED";
    }
    if (suspended(symbol, date)) {
      return "SUSPENDED";
    }
    return bars(symbol, DateRangeRequest.builder().start(date).end(date).build()).isEmpty()
        ? "UNKNOWN"
        : "TRADING";
  }

  /** {@inheritDoc} */
  @Override
  public boolean suspended(String symbol, LocalDate date) {
    var i = instrument(symbol);
    var source = mapper.latestSourceAt(i.getId(), synthetic(), date);
    return source != null
        && mapper
            .suspensions(
                SuspensionQuery.builder()
                    .instrumentId(i.getId())
                    .source(source)
                    .synthetic(synthetic())
                    .startDate(date)
                    .endDate(date)
                    .build())
            .contains(date);
  }

  /** {@inheritDoc} */
  @Override
  public int sync(String symbol, LocalDate start, LocalDate end) {
    var i = instrument(symbol);
    var listed = mapper.listedAt(i.getId());
    if (listed != null && listed.isAfter(start)) {
      start = listed;
    }
    if (start.isAfter(end)) {
      return 0;
    }
    var cached = bars(symbol, DateRangeRequest.builder().start(start).end(end).build());
    String source = cached.isEmpty() ? null : String.valueOf(cached.getFirst().getSource());
    var expectedCached =
        expectedDates(
            TradingDatesQuery.builder()
                .symbol(symbol)
                .start(start)
                .end(end)
                .source(source)
                .build());
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    if (MarketCachePolicy.usable(cached, expectedCached, now, Duration.ofMinutes(cacheMinutes))) {
      acquired(symbol, "FRESH_CACHE", start, end, cached);
      return cached.size();
    }
    if (cacheMinutes > 0
        && !synthetic()
        && MarketCachePolicy.storedHistoricalUsable(cached, expectedCached, now, end)) {
      acquired(symbol, "STORED_HISTORY", start, end, cached);
      return cached.size();
    }
    MarketWindow window;
    try {
      window = provider.fetchWindow(symbol, start, end, "INDEX".equals(i.getType()));
    } catch (RuntimeException e) {
      if (e instanceof com.alphascope.common.task.TaskStoppedException stopped) throw stopped;
      var actual =
          cached.stream()
              .map(MarketBarDO::getTradeDate)
              .collect(java.util.stream.Collectors.toSet());
      var missing = expectedCached.stream().filter(day -> !actual.contains(day)).sorted().toList();
      var summary =
          com.alphascope.market.provider.MarketFailureSummary.describe(
              com.alphascope.market.provider.MarketFailureSummary.Evidence.builder()
                  .symbol(symbol)
                  .name(i.getName())
                  .latestDate(
                      cached.stream()
                          .map(MarketBarDO::getTradeDate)
                          .max(LocalDate::compareTo)
                          .orElse(null))
                  .missingDates(missing)
                  .diagnostic(FailureDiagnostics.from(e))
                  .build());
      var failure =
          new com.alphascope.common.exception.BusinessException(
              com.alphascope.common.response.ResultCode.DATA_UNAVAILABLE, 503, summary);
      failure.initCause(e);
      throw failure;
    }
    if (window.bars().isEmpty()) {
      throw new IllegalStateException("缺少可验证历史行情");
    }
    source = window.bars().getFirst().provider();
    var expected =
        expectedDates(
            TradingDatesQuery.builder().symbol(symbol).start(start).end(end).source(null).build());
    expected.removeAll(window.absences().keySet());
    var actual = new TreeSet<LocalDate>();
    window.bars().forEach(b -> actual.add(b.tradeDate()));
    if (!expected.equals(actual)) {
      throw new IllegalStateException("历史行情存在未知缺口，拒绝写入");
    }
    int count = writer.persist(i.getId(), window);
    acquired(
        symbol,
        "FETCHED",
        start,
        end,
        mapper.bars(
            MarketBarQuery.builder()
                .symbol(symbol)
                .startDate(start)
                .endDate(end)
                .synthetic(synthetic())
                .source(source)
                .build()));
    return count;
  }

  /** {@inheritDoc} */
  @Override
  public List<MarketBarDO> replayBars(String symbol, DateRangeRequest request) {
    DateRangeValidator.requireValid(request.getStart(), request.getEnd());
    if (synthetic()) {
      return List.of();
    }
    var query =
        InstrumentHistoryQuery.builder()
            .symbol(symbol)
            .startDate(request.getStart())
            .endDate(request.getEnd())
            .synthetic(false)
            .build();
    for (String source : mapper.replaySources(query)) {
      if (!provider.acceptsSource(source)) {
        continue;
      }
      return mapper
          .replayBars(
              MarketBarQuery.builder()
                  .symbol(symbol)
                  .startDate(request.getStart())
                  .endDate(request.getEnd())
                  .source(source)
                  .synthetic(false)
                  .build())
          .stream()
          .map(
              revision -> {
                var bar = Jsons.read(revision.getBarJson(), MarketBarDO.class);
                bar.setRevisionId(revision.getId());
                bar.setCreatedAt(revision.getRecordedAt());
                bar.setUpdatedAt(revision.getRecordedAt());
                return bar;
              })
          .toList();
    }
    return List.of();
  }

  /** {@inheritDoc} */
  @Override
  public Set<LocalDate> replayCalendar(DateRangeRequest request) {
    DateRangeValidator.requireValid(request.getStart(), request.getEnd());
    var rows =
        mapper.replayCalendar(
            TradingDatesQuery.builder().start(request.getStart()).end(request.getEnd()).build());
    if (rows.size() != ChronoUnit.DAYS.between(request.getStart(), request.getEnd()) + 1) {
      throw new IllegalStateException("严格时点日历缺少可证明的保存版本");
    }
    return rows.stream()
        .filter(row -> Boolean.TRUE.equals(row.getOpen()))
        .map(row -> row.getTradeDate())
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
