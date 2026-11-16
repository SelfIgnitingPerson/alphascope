package com.alphascope.market.service;

import com.alphascope.market.dto.MarketWindow;

/**
 * 当前视图、因子、修订和停牌证据在同一个短事务中提交。
 *
 * @author chenxuefeng
 */
public interface MarketWriteService {

  /**
   * 在同一短事务内保存当前行情、同源因子、不可变修订、必要审计及停牌证据。
   *
   * <p>调用方在事务外完成网络请求；内容未变的行情保留原批次，停牌清理与对应日线/因子保持一致。
   *
   * @param instrumentId 已登记证券的内部主键
   * @param window 已通过来源、价格、因子及交易日完整性校验的窗口，包含明确停牌证据
   * @return 窗口中成功保存的实际行情条数
   */
  int persist(long instrumentId, MarketWindow window);
}
