package com.alphascope.scheduler.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 统一装配研究任务的动态证据指纹，与队列状态和执行控制分开。
 *
 * @author chenxuefeng
 */
public interface JobSnapshotService {
  /**
   * 读取当前配置与已保存输入，不触发外部采集。
   *
   * @param date 业务日期
   * @return 用于确定性哈希的动态证据文档
   */
  Map<String, Object> capture(LocalDate date);
}
