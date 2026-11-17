package com.alphascope.market.mapper;

import com.alphascope.market.dto.InstrumentLifecycle;
import com.alphascope.market.entity.Instrument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保存证券主数据；代码是公开标识，数据库ID只用于内部关联。
 *
 * @author chenxuefeng
 */
@Mapper
public interface InstrumentAdminMapper {

  /**
   * 新增证券或指数主数据，不在主数据写入时采集行情。
   *
   * @param row 已校验代码后缀、名称、交易所、类型及可空上市日期的证券对象
   * @throws org.springframework.dao.DuplicateKeyException 证券代码已经存在
   */
  void create(Instrument row);

  /**
   * 保存经核验的生命周期原文，代码不变。
   *
   * @param row 已验证日期、来源和原文
   */
  void lifecycle(InstrumentLifecycle row);
}
