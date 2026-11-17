package com.alphascope.operations.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.operations.mapper.PlatformCapacityMapper;
import com.alphascope.operations.service.StorageStatusService;
import com.alphascope.operations.vo.StorageStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * 台账查询不扫描大正文，物理数据库分配与应用磁盘分别观测。
 *
 * @author chenxuefeng
 */
@Service
public class StorageStatusServiceImpl implements StorageStatusService {
  private final CapacityMapper personal;
  private final PlatformCapacityMapper platform;
  private final ProductProperties options;

  public StorageStatusServiceImpl(
      CapacityMapper personal, PlatformCapacityMapper platform, ProductProperties options) {
    this.personal = personal;
    this.platform = platform;
    this.options = options;
  }

  @Override
  public StorageStatus status() {
    if (TenantContext.user() == null || !"ADMIN".equals(TenantContext.user().role()))
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "平台存储检查需要管理员权限");
    try {
      var root = Path.of(System.getProperty("alphascope.root", "."));
      if (Files.isDirectory(root.resolve(".runtime"))) root = root.resolve(".runtime");
      return new StorageStatus(
          personal.storage(TenantContext.id()),
          platform.storage(),
          Files.getFileStore(root).getUsableSpace(),
          options.getMinimumRuntimeFreeBytes(),
          "NOT_OBSERVED_REQUIRES_DATABASE_HOST_MONITOR");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("应用运行资料磁盘状态未能核实", e);
    }
  }
}
