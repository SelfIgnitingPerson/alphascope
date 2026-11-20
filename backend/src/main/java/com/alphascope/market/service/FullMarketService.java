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
   * 使用已入库的授权历史档案复算全 A 观察。
   *
   * @param date 研究日，必须存在该日已核验的进口截面
   */
  void rebuildImported(LocalDate date);

  /**
   * 只核验本机已有原文与实际当日目录，不访问当前通道补造历史。
   *
   * @param input 明确日期与每次最多10日的范围
   * @return 已修复、已完整和逐日缺项
   */
  com.alphascope.market.vo.MarketRepairView repairStored(
      com.alphascope.market.dto.MarketRepairInput input);

  /**
   * 收盘流水线中的可选三所归档，失败保留可解释缺项，不阻断个股报告。
   *
   * @param date 本次已完成交易日
   */
  void refresh(LocalDate date);

  /**
   * 按真实同日归档核对历史窗口、能力和下一步，不触发采集。
   *
   * @param date 所选业务日
   * @return 分阶段就绪程度
   */
  com.alphascope.market.vo.MarketReadinessView readiness(LocalDate date);

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
