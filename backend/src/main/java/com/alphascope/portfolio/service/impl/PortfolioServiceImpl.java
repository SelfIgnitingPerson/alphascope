package com.alphascope.portfolio.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.portfolio.dto.AccountInput;
import com.alphascope.portfolio.dto.PortfolioHistoryQuery;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.dto.PositionInput;
import com.alphascope.portfolio.dto.PositionWriteCommand;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import com.alphascope.portfolio.entity.PortfolioRevision;
import com.alphascope.portfolio.mapper.PortfolioHistoryMapper;
import com.alphascope.portfolio.mapper.PortfolioMapper;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.AccountView;
import com.alphascope.portfolio.vo.PortfolioContext;
import com.alphascope.portfolio.vo.PositionView;
import com.alphascope.portfolio.vo.PositionsView;
import com.alphascope.portfolio.vo.WatchlistView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
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
  private final PortfolioHistoryMapper history;

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

  public PortfolioServiceImpl(
      PortfolioMapper mapper, MarketService market, PortfolioHistoryMapper history) {
    this.history = history;
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
  public PositionsView positionsView(LocalDate date) {
    var value = context(date);
    return new PositionsView(
        value.positions().stream().map(PositionView::from).toList(),
        AccountView.from(value.account()),
        "CNY");
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
  @Transactional
  public void position(String symbol, PositionInput input) {
    if (input.riskGroup() != null
        && (input.riskGroup().length() > 256 || input.riskGroup().split("[,，]").length > 8))
      throw new IllegalArgumentException("人工相关风险标签最多8项、共256字");
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
            .riskGroup(input.riskGroup())
            .asOfDate(date)
            .build());
    saveRevision("POSITION", symbol, date, input);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void account(AccountInput input) {
    var assets = input.totalAssets();
    var date = input.asOfDate();
    if (date == null || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("资产登记日期不能为空或晚于上海当前日期");
    for (var value :
        Stream.of(input.riskBudget(), input.liquidityReserve(), input.cashBalance())
            .filter(Objects::nonNull)
            .toList()) {
      if (value.signum() < 0 || (assets != null && value.compareTo(assets) > 0))
        throw new IllegalArgumentException("预算或用款预留须非负，且不大于已登记总资产");
    }
    if (input.riskBudget() != null && input.riskBudget().scale() > 4)
      throw new IllegalArgumentException("预算金额最多四位小数");
    if (input.fundsPurpose() != null && input.fundsPurpose().length() > 300)
      throw new IllegalArgumentException("资金用途最多300字");
    if (assets != null && assets.signum() <= 0) {
      throw new IllegalArgumentException("账户总资产必须大于0，未知时应留空");
    }
    mapper.accountSave(input);
    saveRevision("ACCOUNT", "ACCOUNT", date, input);
  }

  /** {@inheritDoc} */
  @Override
  public PortfolioContext context(LocalDate date) {
    var now = java.time.LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    if (date == null || date.isAfter(now.toLocalDate()))
      throw new IllegalArgumentException("组合研究日不得为未来");
    var cutoff = date.atTime(java.time.LocalTime.MAX);
    if (cutoff.isAfter(now)) cutoff = now;
    var known = new java.util.HashSet<>(history.recordedSymbols());
    var current = positions();
    var positions = new java.util.LinkedHashMap<String, PositionDO>();
    var account = new PortfolioAccount();
    // 未迁移的旧行只在当前日作为待核对记录，不充当过去已可得证据。
    if (date.equals(now.toLocalDate())) {
      current.stream()
          .filter(
              p ->
                  !known.contains(p.getSymbol())
                      && p.getAsOfDate() != null
                      && !p.getAsOfDate().isAfter(date))
          .forEach(p -> positions.put(p.getSymbol(), p));
      if (!known.contains("ACCOUNT")) account = account();
    }
    for (var row : history.at(new PortfolioHistoryQuery(date, cutoff))) {
      if (!Jsons.hash(row.getEvidenceJson()).equals(row.getEvidenceHash()))
        throw new IllegalStateException("持仓历史指纹不匹配");
      if ("POSITION".equals(row.getRecordKind())) {
        var input = Jsons.read(row.getEvidenceJson(), PositionInput.class);
        var p = new PositionDO();
        p.setSymbol(row.getSymbol());
        p.setName(
            current.stream()
                .filter(v -> row.getSymbol().equals(v.getSymbol()))
                .map(PositionDO::getName)
                .findFirst()
                .orElse(row.getSymbol()));
        p.setRiskGroup(input.riskGroup());
        p.setQuantity(input.quantity());
        p.setCostPrice(input.costPrice());
        p.setAsOfDate(input.asOfDate());
        p.setHistoryVerified(true);
        p.setRecordedAt(row.getCreatedAt());
        positions.put(row.getSymbol(), p);
      } else if ("ACCOUNT".equals(row.getRecordKind())) {
        var input = Jsons.read(row.getEvidenceJson(), AccountInput.class);
        account = new PortfolioAccount();
        account.setCashBalance(input.cashBalance());
        account.setPositionsComplete(input.positionsComplete());
        account.setTotalAssets(input.totalAssets());
        account.setAsOfDate(input.asOfDate());
        account.setRiskBudget(input.riskBudget());
        account.setLiquidityReserve(input.liquidityReserve());
        account.setFundsNeededOn(input.fundsNeededOn());
        account.setFundsPurpose(input.fundsPurpose());
        account.setHistoryVerified(true);
      }
    }
    return new PortfolioContext(java.util.List.copyOf(positions.values()), account);
  }

  private void saveRevision(String kind, String symbol, LocalDate date, Object input) {
    var row = new PortfolioRevision();
    row.setRecordKind(kind);
    row.setSymbol(symbol);
    row.setBusinessDate(date);
    row.setEvidenceJson(Jsons.write(input));
    row.setEvidenceHash(Jsons.hash(row.getEvidenceJson()));
    history.save(row);
  }
}
