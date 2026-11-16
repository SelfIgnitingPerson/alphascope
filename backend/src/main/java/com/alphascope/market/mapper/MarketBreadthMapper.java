package com.alphascope.market.mapper;

import com.alphascope.market.dto.ResearchSnapshotQuery;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保存不可变沪深市场截面，并先定位主键再读取大体积JSON证据。
 *
 * @author chenxuefeng
 */
@Mapper
public interface MarketBreadthMapper {

  /**
   * 读取业务日每个来源及范围的最新不可变清单原文，用于覆盖核验。
   *
   * @param date 精确业务日期
   * @return 最新候选截面，不使用其他日期替代
   */
  List<MarketBreadthSnapshot> coverageCandidates(LocalDate date);

  /**
   * 已定位不可变主键后读取清单原文，避免每次查询重复传输大JSON。
   *
   * @param id 不可变截面主键
   * @return 原始清单及截面JSON
   */
  String coverageRaw(long id);

  /**
   * 保存沪深 A 股市场截面的不可变版本，相同内容重复采集时忽略新增。
   *
   * <p>范围固定为 SH_SZ_A、来源必须明确且与全部原价记录一致，不将北交所或完整全 A 覆盖视为已接入。
   *
   * @param snapshot 交易日、内容指纹、统计指标 JSON、原始证据 JSON 及实际采集时刻
   */
  void save(MarketBreadthSnapshot snapshot);

  /**
   * 读取精确同日同源的最新快照，按需加载原始JSON。
   *
   * @param query 业务日期、允许的来源及大字段加载要求
   * @return 最新快照；没有该来源数据时返回null
   */
  MarketBreadthSnapshot forSource(ResearchSnapshotQuery query);
}
