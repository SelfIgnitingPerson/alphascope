package com.alphascope.market.mapper;

import com.alphascope.market.entity.MarketScore;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 按业务日期、模型及真实/演示模式保存全市场评级与缺项证据。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ScoreMapper {

  /**
   * 保存或更新指定交易日和数据模式的 MARKET_V2 全市场评级及缺项证据。
   *
   * <p>缺失维度不能按 0 分处理或按已有维度放大；UNKNOWN 状态及 null 评分必须原样保留。
   *
   * @param score 交易日、可空综合评分、评级状态、证据 JSON 及真实/演示标志
   */
  void save(MarketScore score);

  /**
   * 读取指定日期区间和模式的 MARKET_V2 评级，按交易日升序返回。
   *
   * @param start 查询区间起始日期，包含当日
   * @param end 查询区间结束日期，包含当日
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 已保存的评分及证据列表；没有记录时返回空列表
   */
  List<MarketScore> scores(
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("synthetic") boolean synthetic);
}
