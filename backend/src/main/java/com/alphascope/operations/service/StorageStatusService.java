package com.alphascope.operations.service;

import com.alphascope.operations.vo.StorageStatus;

/**
 * 精确说明逻辑配额与物理存储的边界。
 *
 * @author chenxuefeng
 */
public interface StorageStatusService {
  /**
   * 管理员只读检查存储总量与保护线。
   *
   * @return 口径独立的聚合指标
   */
  StorageStatus status();
}
