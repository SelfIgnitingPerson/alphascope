package com.alphascope.market.service;

import com.alphascope.market.dto.InstrumentInput;

/**
 * 证券主数据写入的业务校验入口，不承担行情采集。
 *
 * @author chenxuefeng
 */
public interface InstrumentAdminService {

  /**
   * 校验统一代码与交易所后缀一致后登记证券或指数主数据。
   *
   * @param input 统一代码、中文名称、交易所、标的类型及可空上市日期
   * @throws IllegalArgumentException 证券代码与交易所后缀不一致
   */
  void create(InstrumentInput input);
}
