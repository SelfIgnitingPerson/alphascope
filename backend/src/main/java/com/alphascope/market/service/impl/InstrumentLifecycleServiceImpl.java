package com.alphascope.market.service.impl;

import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.InstrumentLifecycle;
import com.alphascope.market.dto.LifecycleInput;
import com.alphascope.market.mapper.InstrumentAdminMapper;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.provider.InstrumentLifecycleProvider;
import com.alphascope.market.service.InstrumentLifecycleService;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 保存带来源、时刻和指纹的生命周期；数据不足明确停止，不伪造上市前行情。
 *
 * @author chenxuefeng
 */
@Service
public class InstrumentLifecycleServiceImpl implements InstrumentLifecycleService {
  private final MarketMapper market;
  private final InstrumentAdminMapper writer;
  private final InstrumentLifecycleProvider provider;
  private final Clock clock;

  public InstrumentLifecycleServiceImpl(
      MarketMapper market,
      InstrumentAdminMapper writer,
      InstrumentLifecycleProvider provider,
      Clock clock) {
    this.market = market;
    this.writer = writer;
    this.provider = provider;
    this.clock = clock;
  }

  @Override
  public void ensure(String symbol) {
    var row = market.instrument(symbol);
    if (row == null || !"STOCK".equals(row.getType()) || row.getListedAt() != null) return;
    var value = provider.fetch(symbol);
    writer.lifecycle(value);
  }

  @Override
  public void declare(String symbol, LifecycleInput input) {
    var row = market.instrument(symbol);
    if (row == null) throw new NotFoundException("证券不存在");
    var url = URI.create(input.evidenceUrl());
    var now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
    if (!"https".equals(url.getScheme())
        || url.getHost() == null
        || url.getUserInfo() != null
        || input.listedAt().isAfter(now.toLocalDate())
        || input.listedAt().isBefore(LocalDate.of(1990, 1, 1))
        || input.delistedAt() != null && input.delistedAt().isBefore(input.listedAt()))
      throw new IllegalArgumentException("生命周期需明确有效日期与公开HTTPS依据");
    writer.lifecycle(
        new InstrumentLifecycle(
            symbol,
            row.getName(),
            input.listedAt(),
            input.delistedAt(),
            "MANUAL_PUBLIC",
            now,
            Jsons.write(Map.of("sourceUrl", input.evidenceUrl(), "reference", input.reference()))));
  }
}
