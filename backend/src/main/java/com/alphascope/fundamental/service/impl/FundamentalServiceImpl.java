package com.alphascope.fundamental.service.impl;

import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.fundamental.mapper.FundamentalMapper;
import com.alphascope.fundamental.service.FundamentalService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * FundamentalService 的业务实现。
 *
 * <p>财报查询的业务入口，集中处理业务日期与实际当前时刻的可用性边界。
 *
 * @author chenxuefeng
 */
@Service
public class FundamentalServiceImpl implements FundamentalService {
  private final FundamentalMapper mapper;

  public FundamentalServiceImpl(FundamentalMapper mapper) {
    this.mapper = mapper;
  }

  /** {@inheritDoc} */
  @Override
  public List<FinancialReport> reports(String symbol, LocalDate date) {
    LocalDateTime cutoff = date.atTime(16, 10);
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    if (cutoff.isAfter(now)) {
      cutoff = now;
    }
    var rows = mapper.reports(symbol, cutoff);
    // MyBatis直接映射Java时间类型，不再通过Map处理JDBC的Timestamp。
    Set<String> seen = new HashSet<>();
    return rows.stream()
        .filter(row -> seen.add(row.getPeriodEnd() + ":" + row.getReportType()))
        .toList();
  }
}
