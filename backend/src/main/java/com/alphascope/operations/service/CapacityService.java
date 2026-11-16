package com.alphascope.operations.service;

import com.alphascope.operations.vo.ResearchCapacity;

/**
 * 在既有事务内执行用户资源预算检查。
 *
 * @author chenxuefeng
 */
public interface CapacityService {
  /**
   * 读取当前用户预算使用量。
   *
   * @return 实际队列及存储状态
   */
  ResearchCapacity status();

  /** 锁住账号并核对日报任务配额，应由入队事务调用。 */
  void requireJobSlot();

  /**
   * 锁住账号并核对验证证据空间，额外预留一个任务最大容量。
   *
   * @param maximumBytes 本任务最大字节数
   */
  void requireEvidenceSpace(long maximumBytes);
}
