package com.alphascope.fundamental.mapper;

import com.alphascope.fundamental.entity.EarningsForecastSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 公开预测实际观测快照持久化；首次可用时间不因重试或页面读取而改变。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ForecastMapper {

  /**
   * 保存公开盈利预测的不可变观测快照，相同内容重复采集时忽略新增。
   *
   * <p>来源固定为 EASTMONEY_PUBLIC；availableAt 同时作为首次采集时刻，重复内容不会刷新原记录的可用时间。
   *
   * @param snapshot 证券主键、观测日期、首次可用时刻、原始链接、内容指纹、指标及原文 JSON
   */
  void save(EarningsForecastSnapshot snapshot);

  /**
   * 读取指定时点以前实际观测到的盈利预测，按可用时刻和主键倒序返回。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param cutoff 可采用公开证据的截止时刻，上海时区，包含该时刻
   * @return 最多 30 份可见快照；没有观测记录时返回空列表
   */
  List<EarningsForecastSnapshot> visible(
      @Param("symbol") String symbol, @Param("cutoff") LocalDateTime cutoff);
}
