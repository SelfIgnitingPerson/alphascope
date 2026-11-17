package com.alphascope.operations.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.operations.mapper.PlatformCapacityMapper;
import com.alphascope.operations.service.CapacityService;
import com.alphascope.operations.vo.ResearchCapacity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 配额只限制新增；冻结报告和研究证据不被自动清理。
 *
 * @author chenxuefeng
 */
@Service
public class CapacityServiceImpl implements CapacityService {
  private final CapacityMapper mapper;
  private final ProductProperties properties;
  private PlatformCapacityMapper platform;

  @Autowired
  public void setPlatform(PlatformCapacityMapper platform) {
    this.platform = platform;
  }

  public CapacityServiceImpl(CapacityMapper mapper, ProductProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  /** {@inheritDoc} */
  @Override
  public ResearchCapacity status() {
    return mapper.capacity(TenantContext.id());
  }

  /** {@inheritDoc} */
  @Override
  public void requireJobSlot() {
    mapper.lockUser(1);
    mapper.lockUser(TenantContext.id());
    if (platform != null && platform.usage().activeJobs() >= properties.getMaximumPlatformJobs())
      throw new BusinessException(ResultCode.CAPACITY_EXCEEDED, 429, "平台研究队列已满，请稍后提交；已有任务仍会继续处理");
    if (status().activeJobs() >= properties.getMaximumJobs())
      throw new BusinessException(ResultCode.CAPACITY_EXCEEDED, 429, "待处理日报任务已达上限，请完成或取消已有任务后再提交");
  }

  /** {@inheritDoc} */
  @Override
  public void requireEvidenceSpace(long maximumBytes) {
    mapper.lockUser(1);
    mapper.lockUser(TenantContext.id());
    var status = status();
    if (platform != null) {
      var total = platform.usage();
      if (total.activeValidations() >= properties.getMaximumPlatformValidations()
          || total.evidenceBytes() + (total.activeValidations() + 1) * maximumBytes
              > properties.getMaximumPlatformEvidenceBytes())
        throw new BusinessException(
            ResultCode.CAPACITY_EXCEEDED, 429, "平台验证或存储预算已满，请等待或联系管理员；不会自动删除历史证据");
    }
    if (status.evidenceBytes() + (status.activeValidations() + 1) * maximumBytes
        > properties.getMaximumEvidenceBytes())
      throw new BusinessException(
          ResultCode.CAPACITY_EXCEEDED, 409, "研究证据预算不足；请先导出并联系管理员调整预算，历史证据不会自动删除");
  }
}
