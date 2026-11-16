package com.alphascope.fundamental.service;

import com.alphascope.common.vo.CollectionView;
import java.time.LocalDate;

/**
 * 财报为增强证据；失败保留状态和已有版本，不中断已具备行情的自选研究，也不填充模拟值。
 *
 * @author chenxuefeng
 */
public interface FinancialSyncService {

  /**
   * 采集证券公开累计财报，在短事务内锁定证券、按内容查重并新增不可变版本；失败保留已有证据。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void sync(String symbol, LocalDate date);

  /**
   * 只读返回指定证券和业务日期的最近财报采集状态，不触发采集。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 已保存状态，或演示不适用、功能关闭、尚未检查等明确状态
   */
  CollectionView status(String symbol, LocalDate date);
}
