package com.alphascope.market.service;

import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.vo.MarketObservation;
import java.time.LocalDate;

/**
 * 三所同日截面与派生观察生产链路，和沪深代理范围分别验收。
 *
 * @author chenxuefeng
 */
public interface FullMarketService {
  /**
   * 采集或复用真实同日截面并计算全A分项。
   *
   * @param date 业务日期
   */
  void sync(LocalDate date);

  /**
   * 读取精确业务日期的三所观察，不触发网络。
   *
   * @param date 业务日期
   * @return 观察或null
   */
  MarketObservation observation(LocalDate date);

  /**
   * 读取精确日期全A领先样本。
   *
   * @param date 业务日期
   * @return 同范围证据或null
   */
  LeaderScoreEvidence leaders(LocalDate date);
}
