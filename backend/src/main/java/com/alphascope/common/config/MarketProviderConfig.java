package com.alphascope.common.config;

import com.alphascope.common.config.AlphaScopeProperties.ProviderType;
import com.alphascope.market.provider.BaoStockMarketDataProvider;
import com.alphascope.market.provider.DemoMarketDataProvider;
import com.alphascope.market.provider.EastmoneyIndexProvider;
import com.alphascope.market.provider.ExchangeCalendarProvider;
import com.alphascope.market.provider.LicensedMarketProvider;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.provider.MultiChannelMarketDataProvider;
import com.alphascope.market.provider.SinaMarketDataProvider;
import com.alphascope.market.provider.TushareMarketDataProvider;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.baostock.BaoStockClient;
import com.alphascope.market.provider.baostock.BaoStockRequestPolicy;
import com.alphascope.market.provider.research.BaoStockResearchProvider;
import com.alphascope.market.provider.research.EastmoneyResearchProvider;
import com.alphascope.market.provider.research.ResearchDataProvider;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.provider.research.SinaSnapshotResearchProvider;
import com.alphascope.market.provider.research.TushareResearchProvider;
import com.alphascope.market.provider.tushare.TushareClient;
import com.alphascope.operations.service.SourceLicenseService;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 按集中校验的配置建立日线和研究能力路由，不在业务服务里构造固定供应商。
 *
 * @author chenxuefeng
 */
@Configuration
@EnableConfigurationProperties(AlphaScopeProperties.class)
public class MarketProviderConfig {
  @Bean
  public Clock researchClock() {
    return Clock.systemUTC();
  }

  @Bean
  public TushareClient tushareClient(Environment environment) {
    return new TushareClient(environment.getProperty("TUSHARE_TOKEN", ""));
  }

  @Bean
  public MarketDataProvider marketDataProvider(
      AlphaScopeProperties properties,
      TushareClient tushare,
      Clock clock,
      Environment environment,
      SourceLicenseService licenses,
      BaoStockClient baoStockClient) {
    if (properties.getProvider() == ProviderType.DEMO) {
      return new DemoMarketDataProvider();
    }
    List<MarketDataProvider> channels = new ArrayList<>();
    for (ProviderType type : channels(properties)) {
      switch (type) {
        case BAOSTOCK -> channels.add(new BaoStockMarketDataProvider(baoStockClient));
        case SINA -> channels.add(new SinaMarketDataProvider());
        case EXCHANGE_CALENDAR -> channels.add(new ExchangeCalendarProvider());
        case EASTMONEY -> channels.add(new EastmoneyIndexProvider());
        case TUSHARE -> {
          if (configured(environment)) {
            channels.add(new TushareMarketDataProvider(tushare));
          } else if (properties.getProvider() == ProviderType.TUSHARE) {
            throw new IllegalArgumentException("TUSHARE_TOKEN未配置");
          }
        }
        default -> throw new IllegalArgumentException("真实组合不能包含演示或递归组合");
      }
    }
    return new MultiChannelMarketDataProvider(
        channels.stream()
            .map(channel -> (MarketDataProvider) new LicensedMarketProvider(channel, licenses))
            .toList(),
        Duration.ofSeconds(properties.getMarket().getCooldownSeconds()),
        clock);
  }

  @Bean
  public ResearchDataRouter researchDataRouter(
      AlphaScopeProperties properties,
      TushareClient tushare,
      Clock clock,
      Environment environment,
      SourceLicenseService licenses,
      BaoStockClient baoStockClient) {
    List<ResearchDataProvider> channels = new ArrayList<>();
    if (properties.getProvider() != ProviderType.DEMO) {
      var configured = channels(properties);
      var publicReference =
          configured.contains(ProviderType.EASTMONEY) || configured.contains(ProviderType.SINA)
              ? new EastmoneyResearchProvider(clock)
              : null;
      for (ProviderType type : configured) {
        if (type == ProviderType.EASTMONEY) {
          channels.add(publicReference);
        }
        if (type == ProviderType.SINA) {
          channels.add(
              new SinaSnapshotResearchProvider(
                  clock, publicReference, !configured.contains(ProviderType.EASTMONEY)));
        }
        if (type == ProviderType.BAOSTOCK) {
          channels.add(new BaoStockResearchProvider(baoStockClient));
        }
        if (type == ProviderType.TUSHARE && configured(environment)) {
          channels.add(new TushareResearchProvider(tushare));
        }
      }
    }
    var router =
        new ResearchDataRouter(
            channels, Duration.ofSeconds(properties.getMarket().getCooldownSeconds()), clock);
    router.setLicenses(licenses);
    return router;
  }

  private List<ProviderType> channels(AlphaScopeProperties properties) {
    var result =
        properties.getProvider() == ProviderType.MULTI
            ? properties.getMarket().getChannels()
            : List.of(properties.getProvider());
    if (result.stream().distinct().count() != result.size()) {
      throw new IllegalArgumentException("行情通道不能重复");
    }
    return result;
  }

  private boolean configured(Environment environment) {
    return !environment.getProperty("TUSHARE_TOKEN", "").isBlank();
  }

  /** 全部行情与研究能力共用同一个串行会话，避免免费节点并发连接。 */
  @Bean(destroyMethod = "close")
  public BaoStockClient baoStockClient(AlphaScopeProperties properties) {
    return new BaoStockClient(
        new BaoStockClient.Settings(
            properties.getMarket().getBaostockHost(),
            properties.getMarket().getBaostockPort(),
            15000,
            BaoStockAccessGuard.standard(),
            BaoStockRequestPolicy.standard(properties.getMarket().getBaostockDailyBudget())));
  }
}
