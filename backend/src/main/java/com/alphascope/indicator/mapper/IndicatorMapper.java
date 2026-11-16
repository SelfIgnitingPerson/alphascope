package com.alphascope.indicator.mapper;

import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 按标的、业务日期及真实/演示模式保存和查询技术指标快照。
 *
 * @author chenxuefeng
 */
@Mapper
public interface IndicatorMapper {

  /**
   * 按证券、日期区间和真实/演示模式读取技术指标，按交易日升序返回。
   *
   * @param query 统一证券代码、闭区间起止日期及真实/演示模式
   * @return 已保存的指标序列；无记录时返回空列表
   */
  List<IndicatorSnapshot> list(InstrumentHistoryQuery query);

  /**
   * 保存指定证券、交易日、算法及模式的技术指标，已存在时更新对应快照。
   *
   * <p>缺少历史窗口的指标保持 null，不能补为 0。
   *
   * @param row 指标值、完整性状态、真实/演示标志、输入指纹和原行情批次
   */
  void save(IndicatorSnapshot row);
}
