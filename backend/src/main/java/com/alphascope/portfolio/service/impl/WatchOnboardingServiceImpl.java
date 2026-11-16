package com.alphascope.portfolio.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.market.service.InstrumentDiscoveryService;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.portfolio.dto.WatchDiscoveryInput;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.service.WatchOnboardingService;
import com.alphascope.portfolio.vo.WatchDiscoveryResult;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.service.JobService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 先完成目录验证，再短事务保存自选；任务排队独立返回明确结果。
 *
 * @author chenxuefeng
 */
@Service
public class WatchOnboardingServiceImpl implements WatchOnboardingService {
  private final InstrumentDiscoveryService discovery;
  private final PortfolioService portfolio;
  private final JobService jobs;
  private final AccessProperties properties;
  private final CapacityMapper capacity;
  private final TransactionTemplate tx;

  public WatchOnboardingServiceImpl(
      InstrumentDiscoveryService discovery,
      PortfolioService portfolio,
      JobService jobs,
      AccessProperties properties,
      CapacityMapper capacity,
      TransactionTemplate tx) {
    this.discovery = discovery;
    this.portfolio = portfolio;
    this.jobs = jobs;
    this.properties = properties;
    this.capacity = capacity;
    this.tx = tx;
  }

  /** {@inheritDoc} */
  @Override
  public WatchDiscoveryResult add(WatchDiscoveryInput input) {
    if (input.date().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "初始化日期不能晚于今天");
    discovery.register(input.symbol());
    tx.executeWithoutResult(
        status -> {
          capacity.lockUser(TenantContext.id());
          var list = portfolio.watchlist();
          if (list.stream().noneMatch(item -> item.getSymbol().equals(input.symbol()))
              && list.size() >= properties.getMaximumWatchlist())
            throw new BusinessException(ResultCode.CAPACITY_EXCEEDED, 409, "自选数量已达上限，请先移除不再研究的标的");
          portfolio.watch(input.symbol(), 0);
        });
    if (!input.initialize())
      return new WatchDiscoveryResult(input.symbol(), null, "已加入自选，可在任务中心选择业务日初始化研究");
    try {
      long id =
          jobs.submit(
              JobSubmissionCommand.builder()
                  .reportType("DAILY")
                  .businessDate(input.date())
                  .force(false)
                  .build());
      return new WatchDiscoveryResult(input.symbol(), id, "已加入自选，首次研究已排队；采集结果请查看任务进度");
    } catch (RuntimeException error) {
      return new WatchDiscoveryResult(input.symbol(), null, "已加入自选；首次研究暂未排队，请在任务中心重试");
    }
  }
}
