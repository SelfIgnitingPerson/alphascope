package com.alphascope.fundamental.mapper;

import com.alphascope.fundamental.entity.FinancialReport;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询业务时点可见的累计财报，并按同报告期选择可用版本。
 *
 * @author chenxuefeng
 */
@Mapper
public interface FundamentalMapper {

  /**
   * 读取披露时刻和实际可用时刻均不晚于截止时间的累计财报版本。
   *
   * <p>保留同一报告期的可见版本，不在查询时用当前最新修订替换旧报告的冻结证据。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param cutoff 可采用公开证据的截止时刻，上海时区，包含该时刻
   * @return 按报告期和版本倒序排列的最多 100 条财报；无可见证据时返回空列表
   */
  List<FinancialReport> reports(
      @Param("symbol") String symbol, @Param("cutoff") LocalDateTime cutoff);
}
