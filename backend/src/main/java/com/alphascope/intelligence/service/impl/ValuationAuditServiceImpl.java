package com.alphascope.intelligence.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.fundamental.service.InvestmentEvidenceService;
import com.alphascope.intelligence.calculator.ValuationAuditCalculator;
import com.alphascope.intelligence.dto.ValuationAuditInput;
import com.alphascope.intelligence.service.ValuationAuditService;
import com.alphascope.intelligence.vo.ValuationAuditView;
import com.alphascope.market.service.MarketService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 所有价格与财报从服务端已保存材料读取，页面刷新不请求供应商。
 *
 * @author chenxuefeng
 */
@Service
public class ValuationAuditServiceImpl implements ValuationAuditService {
  private final FundamentalService financials;
  private final InvestmentEvidenceService evidence;
  private final MarketService market;

  public ValuationAuditServiceImpl(
      FundamentalService financials, InvestmentEvidenceService evidence, MarketService market) {
    this.financials = financials;
    this.evidence = evidence;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  public ValuationAuditView read(EvidenceQuery query) {
    if (query.asOf() == null || query.asOf().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("估值核验截止日不能为未来");
    var thesis = evidence.thesis(query);
    var peers = new ArrayList<ValuationAuditInput.PeerInput>();
    if (thesis != null && thesis.peers() != null) {
      if (thesis.peers().size() > 8) throw new IllegalArgumentException("最多8个同行");
      for (var chosen : thesis.peers())
        try {
          var bars =
              market.bars(
                  chosen.symbol(),
                  DateRangeRequest.builder().start(query.asOf()).end(query.asOf()).build());
          peers.add(
              new ValuationAuditInput.PeerInput(
                  chosen.symbol(),
                  financials.reports(chosen.symbol(), query.asOf()),
                  bars.size() == 1 ? bars.getFirst() : null));
        } catch (NotFoundException missing) {
          peers.add(new ValuationAuditInput.PeerInput(chosen.symbol(), List.of(), null));
        }
    }
    return ValuationAuditCalculator.calculate(
        new ValuationAuditInput(
            query.asOf(), thesis, financials.reports(query.symbol(), query.asOf()), peers));
  }
}
