package com.alphascope.scheduler.service;

/**
 * 以已核验日历选择最近收盘日期，按用户提交有预算的自动研究任务。
 *
 * @author chenxuefeng
 */
public interface ScheduledUpdateService {
  /** 补交最近已收盘交易日的日报，窗口采集同时补齐缺失历史；不重复成功任务。 */
  void daily();

  /** 提交本周已收盘的周报，仍保留独立的用户、日期及尝试预算。 */
  void weekly();
}
