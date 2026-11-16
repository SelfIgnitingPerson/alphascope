package com.alphascope.scheduler.service.impl;

import com.alphascope.identity.context.TenantExecutor;
import com.alphascope.market.service.MarketService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.scheduler.service.ScheduledUpdateService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 日历与自动提交的唯一业务入口；启动、收盘和补跑复用，不在启动线程执行完整研究。
 *
 * @author chenxuefeng
 */
@Service
public class ScheduledUpdateServiceImpl implements ScheduledUpdateService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledUpdateServiceImpl.class);
  private final JobService jobs;
  private final MarketService market;
  private final PortfolioService portfolio;
  private final TenantExecutor tenants;
  private final Clock clock;

  public ScheduledUpdateServiceImpl(
      JobService jobs,
      MarketService market,
      PortfolioService portfolio,
      TenantExecutor tenants,
      Clock clock) {
    this.jobs = jobs;
    this.market = market;
    this.portfolio = portfolio;
    this.tenants = tenants;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public void daily() {
    submit("DAILY");
  }

  /** {@inheritDoc} */
  @Override
  public void weekly() {
    submit("WEEKLY");
  }

  private void submit(String type) {
    try {
      var now = clock.instant().atZone(ZoneId.of("Asia/Shanghai"));
      LocalDate reference =
          now.toLocalTime().isBefore(LocalTime.of(16, 10))
              ? now.toLocalDate().minusDays(1)
              : now.toLocalDate();
      LocalDate start =
          type.equals("WEEKLY") ? reference.with(DayOfWeek.MONDAY) : reference.minusDays(14);
      market.syncCalendar(start, reference);
      LocalDate latest = market.lastOpen(start, reference);
      if (latest == null) return;
      tenants.each(
          () -> {
            if (portfolio.watchlist().stream()
                .noneMatch(row -> Boolean.TRUE.equals(row.getEnabled()))) return;
            jobs.submit(
                JobSubmissionCommand.builder()
                    .reportType(type)
                    .businessDate(latest)
                    .force(false)
                    .automatic(true)
                    .build());
          });
    } catch (RuntimeException error) {
      LOGGER.error(
          "scheduled_update_not_submitted type={} reason={}",
          type,
          error.getClass().getSimpleName(),
          error);
    }
  }
}
