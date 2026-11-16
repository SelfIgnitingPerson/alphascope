package com.alphascope.common.mapper;

import com.alphascope.common.entity.ResearchCollection;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 跨财报与市场截面共享的采集状态；原始证据由各自业务表持有。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ResearchCollectionMapper {

  /**
   * 按数据类别、范围和业务日期读取最近一次采集状态，不触发上游请求。
   *
   * @param kind 采集数据类别，例如 FINANCIAL、EARNINGS_FORECAST 或 MARKET_WINDOW
   * @param scope 采集对象代码或市场范围，例如 300476.SZ 或 SH_SZ_A
   * @param date 对应的业务日期，按上海时区解释
   * @return 最近采集记录；从未记录时返回 null
   */
  ResearchCollection collection(
      @Param("kind") String kind, @Param("scope") String scope, @Param("date") LocalDate date);

  /**
   * 新增或更新同一采集对象的状态、证据 JSON 和更新时间，不覆盖原始行情或财报。
   *
   * @param collection 数据类别、采集对象范围、业务日期、最近状态及证据 JSON
   */
  void collectionSave(ResearchCollection collection);
}
