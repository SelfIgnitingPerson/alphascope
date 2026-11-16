package com.alphascope.operations.service;

import com.alphascope.operations.maintenance.DatabaseMaintenance.BackupOutcome;

/**
 * 应用内定期备份与实际恢复验证，不依赖外部助手在线或另建调度任务。
 *
 * @author chenxuefeng
 */
public interface DatabaseBackupService {
  /**
   * 创建一致性备份并在隔离MySQL核对恢复结果，验证成功后执行保留策略。
   *
   * @return 不含凭据和业务内容的已验证备份元信息
   * @throws Exception 导出、Docker恢复或指纹核对失败
   */
  BackupOutcome backupAndVerify() throws Exception;

  /**
   * 在独立备份执行器中请求一次备份，当前已有请求时不重复排队。
   *
   * @return QUEUED或ALREADY_RUNNING
   */
  String requestBackup();

  /**
   * 读取本实例实际执行或已接收的备份请求，不只根据上次状态文件猜测。
   *
   * @return 当前存在实际执行或排队时为true
   */
  boolean isActive();
}
