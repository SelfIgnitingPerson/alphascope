package com.alphascope.market.service;

import com.alphascope.market.dto.LifecycleInput;

/**
 * 生命周期核验和有依据的管理员补录，不触发价格采集。
 *
 * @author chenxuefeng
 */
public interface InstrumentLifecycleService {
  /**
   * 缺少上市日期时采集并保存真实元数据。
   *
   * @param symbol 已登记证券
   */
  void ensure(String symbol);

  /**
   * 显式补录有公开依据的生命周期。
   *
   * @param symbol 已登记代码
   * @param input 已核验日期及依据
   */
  void declare(String symbol, LifecycleInput input);
}
