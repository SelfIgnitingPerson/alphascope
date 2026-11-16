package com.alphascope.portfolio.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.dto.PositionInput;
import com.alphascope.portfolio.dto.PositionWriteCommand;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import com.alphascope.portfolio.mapper.PortfolioMapper;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.AccountView;
import com.alphascope.portfolio.vo.PositionView;
import com.alphascope.portfolio.vo.PositionsView;
import com.alphascope.portfolio.vo.WatchlistView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PortfolioService 的业务实现。
 *
 * <p>管理当前关注范围和人工资产；默认只研究自选，不评估个人持仓集中度。
 *
 * @author chenxuefeng
 */
@Service
public class PortfolioServiceImpl implements PortfolioService {
  private final PortfolioMapper mapper;
  private final MarketService market;
  private AccessProperties access;
  private CapacityMapper capacity;

  @Autowired
  public void setQuota(AccessProperties access, CapacityMapper capacity) {
    this.access = access;
    this.capacity = capacity;
  }

  @Value("${alphascope.research.position-risk-enabled:false}")
  private boolean positionRiskEnabled;

  /** {@inheritDoc} */
  @Override
  public boolean positionRiskEnabled() {
    return positionRiskEnabled;
  }

  public PortfolioServiceImpl(PortfolioMapper mapper, MarketService market) {
    this.mapper = mapper;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  public List<WatchlistView> watchlistView() {
    return watchlist().stream().map(WatchlistView::from).toList();
  }

  /** {@inheritDoc} */
  @Override
  public PositionsView positionsView() {
    return new PositionsView(
        positions().stream().map(PositionView::from).toList(), AccountView.from(account()), "CNY");
  }

  /** {@inheritDoc} */
  @Override
  public List<WatchlistDO> watchlist() {
    return mapper.watchlist();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void watch(String symbol, int priority) {
    if (capacity != null) {
      capacity.lockUser(TenantContext.id());
      var existing = mapper.watchlist();
      if (existing.stream().noneMatch(row -> row.getSymbol().equals(symbol))
          && existing.size() >= access.getMaximumWatchlist())
        throw new BusinessException(ResultCode.CAPACITY_EXCEEDED, 409, "自选数量已达上限，请先移除不再研究的标的");
    }
    mapper.watch(market.instrument(symbol).getId(), priority);
  }

  /** {@inheritDoc} */
  @Override
  public void unwatch(String symbol) {
    mapper.unwatch(symbol);
  }

  /** {@inheritDoc} */
  @Override
  public List<PositionDO> positions() {
    return mapper.positions();
  }

  /** {@inheritDoc} */
  @Override
  public PortfolioAccount account() {
    var a = mapper.account();
    return a == null ? new PortfolioAccount() : a;
  }

  /** {@inheritDoc} */
  @Override
  public void position(String symbol, PositionInput input) {
    BigDecimal quantity = input.quantity();
    BigDecimal cost = input.costPrice();
    LocalDate date = input.asOfDate();
    if (quantity == null
        || cost == null
        || date == null
        || quantity.signum() < 0
        || cost.signum() < 0
        || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) {
      throw new IllegalArgumentException("持仓数量、成本须非负，业务日期不能为空或晚于当前日期");
    }
    mapper.position(
        PositionWriteCommand.builder()
            .instrumentId(market.instrument(symbol).getId())
            .quantity(quantity)
            .costPrice(cost)
            .asOfDate(date)
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public void account(BigDecimal assets, LocalDate date) {
    if (assets != null && assets.signum() <= 0) {
      throw new IllegalArgumentException("账户总资产必须大于0，未知时应留空");
    }
    mapper.accountSave(assets, date);
  }
}
