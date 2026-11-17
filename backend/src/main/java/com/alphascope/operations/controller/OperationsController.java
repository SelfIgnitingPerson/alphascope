package com.alphascope.operations.controller;

import com.alphascope.common.response.Result;
import com.alphascope.operations.policy.OperationalData;
import com.alphascope.operations.service.DatabaseBackupService;
import com.alphascope.operations.service.OperationsStatusService;
import com.alphascope.operations.service.StorageStatusService;
import com.alphascope.operations.vo.OperationsStatusView;
import com.alphascope.operations.vo.StorageStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本机运维状态和显式异步备份入口；GET不采集行情或执行恢复。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
public class OperationsController {
  private final OperationsStatusService status;
  private final DatabaseBackupService backup;

  @org.springframework.beans.factory.annotation.Autowired private StorageStatusService storage;

  /**
   * 只读检查实际逻辑预算、预留、物理分配与运行资料磁盘。
   *
   * @return 管理员聚合指标
   */
  @GetMapping("/api/v1/admin/operations/storage")
  public Result<StorageStatus> storage() {
    return Result.success(storage.status());
  }

  public OperationsController(OperationsStatusService status, DatabaseBackupService backup) {
    this.status = status;
    this.backup = backup;
  }

  /**
   * 查询业务日期、标的数据时效、备份及第二存储情况。
   *
   * @return 运维总览
   */
  @GetMapping("/api/v1/admin/operations")
  public Result<OperationsStatusView> status() {
    return Result.success(status.status());
  }

  /**
   * 在独立备份执行器中请求备份和隔离恢复验证，不重复排队。
   *
   * @return 已接收或正在运行的状态
   */
  @PostMapping("/api/v1/admin/operations/backup")
  public Result<String> backup() {
    return Result.success(backup.requestBackup());
  }
}
