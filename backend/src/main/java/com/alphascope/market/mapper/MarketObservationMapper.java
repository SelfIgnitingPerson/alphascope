package com.alphascope.market.mapper;

import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.entity.MarketFactorSnapshot;
import com.alphascope.market.entity.MarketObservationSnapshot;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保存复权事件版本和独立市场观察，保留原文指纹及实际核验时间。
 *
 * @author chenxuefeng
 */
@Mapper
public interface MarketObservationMapper {

  /**
   * 读取已核验日期覆盖到指定业务日的供应商复权事件快照。
   *
   * @param request 证券、与原价一致的来源和完整核验区间
   * @return 按核验覆盖日和主键倒序选出的快照；无满足条件的版本时返回 null
   */
  MarketFactorSnapshot factor(ResearchFactorRequest request);

  /**
   * 保存复权事件原文版本；同一内容已存在时只推进核验范围和最后核验时间。
   *
   * <p>来源与原价一致；用本次 fetchedAt 记录核验时刻，但重复内容保留原记录的首次 fetched_at 和原文。
   *
   * @param snapshot 供应商证券代码、核验截止日期、原文指纹、原文 JSON 及本次核验时刻
   */
  void factorSave(MarketFactorSnapshot snapshot);

  /**
   * 读取业务日期不晚于指定日期的最新沪深独立观察，不用未来记录倒填。
   *
   * @param date 对应的业务日期，按上海时区解释
   * @return 最近业务日期及主键对应的观察；没有历史记录时返回 null
   */
  MarketObservationSnapshot latestOnOrBefore(LocalDate date);

  /**
   * 保存沪深独立观察的不可变分析和证据快照，相同内容重复保存时忽略新增。
   *
   * <p>范围固定为 SH_SZ_A，模型固定为 SH_SZ_OBSERVATION_V1。
   *
   * @param snapshot 交易日、内容指纹、分项分析 JSON、输入证据 JSON 及实际采集时间
   */
  void save(MarketObservationSnapshot snapshot);

  /**
   * 精确读取三所观察；历史不足不换用沪深范围。
   *
   * @param date 业务日期
   * @return 全A观察或null
   */
  MarketObservationSnapshot fullForDate(LocalDate date);

  /**
   * 批量读取同源且覆盖窗口的因子，避免每个证券重复查询。
   *
   * @param window 范围、来源与核验截止
   * @return 按证券及新版本倒序的已核验因子
   */
  java.util.List<MarketFactorSnapshot> factorsForWindow(ResearchFactorRequest window);
}
