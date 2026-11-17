package com.alphascope.market.mapper;

import com.alphascope.market.entity.MarketUniverseSnapshot;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 北交所目录的不可变归档；只读按精确日期定位，不从当前名单推导历史。
 *
 * @author chenxuefeng
 */
@Mapper
public interface MarketUniverseMapper {
  /**
   * 新增来源和日期明确的清单，相同指纹不会重复新增。
   *
   * @param snapshot 成员、原文、观测时刻与内容指纹
   */
  void save(MarketUniverseSnapshot snapshot);

  /**
   * 读取指定日期北交所目录的最新归档元数据，不加载分页原文。
   *
   * @param date 精确上海业务日期
   * @return 归档元数据或null
   */
  MarketUniverseSnapshot forDate(LocalDate date);

  /**
   * 按已定位的不可变主键读取成员和原文，供指纹核验。
   *
   * @param id 已定位的归档主键
   * @return 完整归档或null
   */
  MarketUniverseSnapshot evidence(long id);
}
