package com.alphascope.industry.service.impl;

import com.alphascope.industry.dto.MembershipInput;
import com.alphascope.industry.dto.MembershipWriteCommand;
import com.alphascope.industry.mapper.MembershipMapper;
import com.alphascope.industry.service.MembershipService;
import com.alphascope.market.service.MarketService;
import org.springframework.stereotype.Service;

/**
 * MembershipService 的业务实现。
 *
 * <p>校验标的、主题与起止日期后登记人工归属，不从当前名单推造历史。
 *
 * @author chenxuefeng
 */
@Service
public class MembershipServiceImpl implements MembershipService {
  private final MembershipMapper mapper;
  private final MarketService market;

  public MembershipServiceImpl(MembershipMapper mapper, MarketService market) {
    this.mapper = mapper;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  public void add(MembershipInput input) {
    if (input.effectiveTo() != null && input.effectiveTo().isBefore(input.effectiveFrom())) {
      throw new IllegalArgumentException("主题归属失效日期不能早于生效日期");
    }
    if (mapper.add(
            MembershipWriteCommand.builder()
                .instrumentId(market.instrument(input.symbol()).getId())
                .themeCode(input.themeCode())
                .effectiveFrom(input.effectiveFrom())
                .effectiveTo(input.effectiveTo())
                .build())
        != 1) {
      throw new IllegalArgumentException("主题不存在");
    }
  }
}
