package com.alphascope.fundamental.service.impl;

import com.alphascope.fundamental.dto.FinancialInput;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.fundamental.mapper.FinancialWriteMapper;
import com.alphascope.fundamental.service.FinancialWriteService;
import com.alphascope.market.service.MarketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FinancialWriteService 的业务实现。
 *
 * <p>校验财报标的、报告期、版本和公开来源，在事务中新增累计财报。
 *
 * @author chenxuefeng
 */
@Service
public class FinancialWriteServiceImpl implements FinancialWriteService {
  private final FinancialWriteMapper mapper;
  private final MarketService market;

  public FinancialWriteServiceImpl(FinancialWriteMapper mapper, MarketService market) {
    this.mapper = mapper;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void insert(FinancialInput input) {
    if (input.periodEnd().isAfter(input.publishedAt().toLocalDate())) {
      throw new IllegalArgumentException("财报期末不能晚于公开披露日期");
    }
    FinancialReport row = new FinancialReport();
    row.setPeriodEnd(input.periodEnd());
    row.setReportType(input.reportType());
    row.setPublishedAt(input.publishedAt());
    row.setVersion(input.version());
    row.setRevenue(input.revenue());
    row.setNetProfit(input.netProfit());
    row.setOperatingCashflow(input.operatingCashflow());
    row.setGrossMargin(input.grossMargin());
    row.setRevenueYoy(input.revenueYoy());
    row.setProfitYoy(input.profitYoy());
    row.setSource(input.source());
    row.setInstrumentId(market.instrument(input.symbol()).getId());
    mapper.lockInstrument(row.getInstrumentId());
    mapper.insert(row);
  }
}
