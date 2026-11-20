package com.alphascope.fundamental.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.dto.CorporateActionInput;
import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.dto.ValuationThesisInput;
import com.alphascope.fundamental.entity.ResearchEvidenceRecord;
import com.alphascope.fundamental.mapper.InvestmentEvidenceMapper;
import com.alphascope.fundamental.service.InvestmentEvidenceService;
import com.alphascope.market.service.MarketService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 不可变证据按内容去重；用户输入是可追溯假设，不作为官方核验或历史时点证明。
 *
 * @author chenxuefeng
 */
@Service
public class InvestmentEvidenceServiceImpl implements InvestmentEvidenceService {
  private final InvestmentEvidenceMapper mapper;
  private final MarketService market;

  public InvestmentEvidenceServiceImpl(InvestmentEvidenceMapper mapper, MarketService market) {
    this.mapper = mapper;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void saveAction(CorporateActionInput input) {
    market.instrument(input.symbol());
    if (input.paymentDate().isBefore(input.exDate())
        || input.shareListingDate().isBefore(input.exDate()))
      throw new IllegalArgumentException("到账日和新增股份上市日不能早于除权日");
    if (input.shareMultiplier().compareTo(java.math.BigDecimal.ONE) > 0
        && !input.shareListingDate().equals(input.exDate()))
      throw new IllegalArgumentException("新增股份非除权日可交易的行动暂不支持回放，请保留缺项；不能提前卖出未上市股份");
    mapper.saveAction(row(input.symbol(), input.exDate(), input));
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void saveThesis(ValuationThesisInput input) {
    market.instrument(input.symbol());
    if (input.asOf().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))
        || input.validUntil().isBefore(input.asOf())
        || input.validUntil().isAfter(input.asOf().plusYears(1)))
      throw new IllegalArgumentException("假设截止日不能是未来，复核有效期须为研究日后一年以内");
    if (input.epsLow().compareTo(input.epsHigh()) > 0
        || input.peLow().compareTo(input.peHigh()) > 0)
      throw new IllegalArgumentException("保守情景不能高于乐观情景");
    if (input.peers() != null
        && (input.peers().size() > 8 || input.peers().stream().anyMatch(java.util.Objects::isNull)))
      throw new IllegalArgumentException("同行最多8家，不能包含空项");
    var policy = input.exitPolicy();
    if (policy != null && policy.minimumProfitYoy() == null && !policy.checkNegativeCashflow())
      throw new IllegalArgumentException("请至少选择一项长期财报失效条件");
    mapper.saveThesis(row(input.symbol(), input.asOf(), input));
  }

  /** {@inheritDoc} */
  @Override
  public List<CorporateActionInput> actions(EvidenceQuery query) {
    return mapper.actions(query).stream()
        .map(row -> checked(row, CorporateActionInput.class))
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public ValuationThesisInput thesis(EvidenceQuery query) {
    var value = mapper.thesis(query);
    return value == null ? null : checked(value, ValuationThesisInput.class);
  }

  /** {@inheritDoc} */
  @Override
  public ValuationThesisInput thesisAtClose(EvidenceQuery query) {
    var value = mapper.thesisAtClose(query);
    if (value == null) return null;
    var thesis = checked(value, ValuationThesisInput.class);
    return thesis.validUntil().isBefore(query.asOf()) ? null : thesis;
  }

  private ResearchEvidenceRecord row(String symbol, LocalDate date, Object input) {
    var row = new ResearchEvidenceRecord();
    row.setSymbol(symbol);
    row.setBusinessDate(date);
    row.setEvidenceJson(Jsons.write(input));
    row.setEvidenceHash(Jsons.hash(row.getEvidenceJson()));
    return row;
  }

  private <T> T checked(ResearchEvidenceRecord row, Class<T> type) {
    if (!Jsons.hash(row.getEvidenceJson()).equals(row.getEvidenceHash()))
      throw new IllegalStateException("投资研究证据指纹不一致");
    return Jsons.read(row.getEvidenceJson(), type);
  }
}
