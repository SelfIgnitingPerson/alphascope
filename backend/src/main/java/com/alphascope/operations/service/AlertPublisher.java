package com.alphascope.operations.service;

import com.alphascope.operations.vo.WorkspaceStatusView;

/**
 * 可选外部运维告警；仅发送状态代码，不发送自选名称、价格或报告。
 *
 * @author chenxuefeng
 */
public interface AlertPublisher {
  /**
   * 状态变化时发布，失败保持重试状态；未配置不联网。
   *
   * @param value 当前用户研究运行状态
   */
  void publish(WorkspaceStatusView value);

  /**
   * 当前是否配置外部接收地址，不公开地址或Token。
   *
   * @return 已配置为true
   */
  boolean configured();
}
