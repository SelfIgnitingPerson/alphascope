package com.alphascope.market.provider.research;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.baostock.BaoStockClient;
import java.time.LocalDate;
import java.util.Set;

/**
 * BaoStock研究能力适配，全部请求继续通过持久访问保护和受控协议客户端。
 *
 * @author chenxuefeng
 */
public class BaoStockResearchProvider implements ResearchDataProvider {
  private final BaoStockClient client;

  public BaoStockResearchProvider(BaoStockClient client) {
    this.client = client;
  }

  /** {@inheritDoc} */
  @Override
  public String code() {
    return "BAOSTOCK";
  }

  /** {@inheritDoc} */
  @Override
  public Set<MarketCapability> capabilities() {
    return Set.of(MarketCapability.MARKET_SNAPSHOT, MarketCapability.ADJUSTMENT_FACTORS);
  }

  /** {@inheritDoc} */
  @Override
  public ResearchMarketSnapshot snapshot(LocalDate date) {
    ExecutionContext.checkpoint();
    var universe = client.query("35", "query_all_stock", 8, date.toString());
    var daily = client.dailyStocks(date);
    return ResearchMarketSnapshot.builder()
        .source(code())
        .date(date)
        .universe(universe)
        .daily(daily)
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public ResearchFactorWindow factors(ResearchFactorRequest request) {
    ExecutionContext.checkpoint();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      ExecutionContext.checkpoint();
      throw new IllegalStateException("BAOSTOCK_INTERRUPTED", e);
    }
    String raw =
        Jsons.write(
            client.query(
                "15",
                "query_adjust_factor",
                10,
                request.symbol(),
                "1990-01-01",
                request.end().toString()));
    return new ResearchFactorWindow(code(), LocalDate.of(1990, 1, 1), request.end(), raw, raw);
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    client.close();
  }
}
