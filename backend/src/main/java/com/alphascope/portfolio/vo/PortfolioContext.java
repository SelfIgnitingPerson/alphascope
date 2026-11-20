package com.alphascope.portfolio.vo;

import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import java.util.List;

/**
 * 按实际记录时点恢复的私人组合，未知记录不倒填。
 *
 * @author chenxuefeng
 * @param positions 当时已登记的最新持仓
 * @param account 当时已登记账户
 */
public record PortfolioContext(List<PositionDO> positions, PortfolioAccount account) {}
