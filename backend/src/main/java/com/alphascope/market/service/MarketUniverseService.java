package com.alphascope.market.service;

import com.alphascope.market.vo.MarketUniverseView;
import java.time.LocalDate;

/**
 * 北交所目录采集与精确日期归档，不改写证券、自选、报告或历史行情。
 *
 * @author chenxuefeng
 */
public interface MarketUniverseService {
  /**
   * 显式采集当前日期目录或复用该日归档；历史缺档时拒绝倒填。
   *
   * @param date 上海目录观测日期
   */
  void sync(LocalDate date);

  /**
   * 日报的可选目录刷新；保留具体诊断，缺档不阻断其他独立研究，不倒填历史。
   *
   * @param date 日报实际业务日期
   */
  void refresh(LocalDate date);

  /**
   * 只读核对指定日期的真实归档，不发起任何供应商请求。
   *
   * @param date 精确业务日期
   * @return 已归档成员与指纹，或者明确未归档状态
   */
  MarketUniverseView snapshot(LocalDate date);
}
