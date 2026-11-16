package com.alphascope.market.service;

import com.alphascope.market.vo.UniverseCoverageView;
import java.time.LocalDate;

/**
 * 从实际保存的真实市场清单核对完整全A覆盖。
 *
 * @author chenxuefeng
 */
public interface UniverseCoverageService {
  /**
   * 读取同日证据并逐证券核对，GET不触发上游请求。
   *
   * @param date 目标业务日
   * @return 完整证明或具体缺口
   */
  UniverseCoverageView inspect(LocalDate date);
}
