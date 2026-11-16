package com.alphascope.market.service;

import com.alphascope.market.vo.MarketBreadthView;
import java.time.LocalDate;
import java.util.List;

/**
 * 核对沪深全量证券清单与单日日线，持久化成交额、涨跌家数及覆盖证据。
 *
 * @author chenxuefeng
 */
public interface MarketBreadthService {

  /**
   * 尝试采集真实沪深市场截面，核对当日证券清单与覆盖率；失败时只记录状态并保留已有快照。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void sync(LocalDate date);

  /**
   * 按给定日期列表复用单会话逐日采集历史截面，受上游访问保护限制时停止继续请求。
   *
   * @param dates 需要采集的真实交易日期列表，按调用方给定顺序处理
   */
  void syncHistory(List<LocalDate> dates);

  /**
   * 只读返回指定日期最新保存的沪深市场截面及状态，不触发采集。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 带明确覆盖范围的市场统计；没有数据时保留未检查、不可用或演示不适用状态
   */
  MarketBreadthView snapshot(LocalDate date);

  /**
   * 校验复用或顺序采集与当前原价来源一致的历史截面。
   *
   * @param dates 需要覆盖的实际开市日期
   * @param source 已选定窗口的实际来源，null时按配置优先级选择
   */
  void syncHistory(List<LocalDate> dates, String source);
}
