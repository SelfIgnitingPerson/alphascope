package com.alphascope.operations.service;

import com.alphascope.operations.vo.OperationsStatusView;

/**
 * 只读核查业务数据时效及备份完整性，不发起上游请求或Docker恢复。
 *
 * @author chenxuefeng
 */
public interface OperationsStatusService {
  /**
   * 核查当前业务时效、上次备份结果及第二存储状态。
   *
   * @return 不含数据库凭据或密钥的运维总览
   */
  OperationsStatusView status();
}
