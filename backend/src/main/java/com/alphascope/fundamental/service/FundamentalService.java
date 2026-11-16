package com.alphascope.fundamental.service;

import com.alphascope.fundamental.entity.FinancialReport;
import java.time.LocalDate;
import java.util.List;

/**
 * 财报查询的业务入口，集中处理业务日期与实际当前时刻的可用性边界。
 *
 * @author chenxuefeng
 */
public interface FundamentalService {

  /**
   * 按业务日 16:10 与实际当前时刻的较早者查询已公开财报，同报告期保留当时可见的最高版本。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 按报告期倒序的累计财报；没有可见证据时为空列表
   */
  List<FinancialReport> reports(String symbol, LocalDate date);
}
