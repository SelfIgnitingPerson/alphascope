package com.alphascope.market.controller;

import com.alphascope.common.response.Result;
import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.service.ProviderProbeService;
import com.alphascope.market.vo.ProviderProbeView;
import com.alphascope.operations.policy.OperationalData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员显式测试入口；保留会话、CSRF、写限频及共享供应商使用保护。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1/admin/market/providers")
public class ProviderProbeController {
  private final ProviderProbeService service;

  public ProviderProbeController(ProviderProbeService service) {
    this.service = service;
  }

  /**
   * 请求一次单通道基础能力核验；普通状态GET不会调用此方法。
   *
   * @param request 通道、能力和精确日期，不接受任意地址或历史大窗口
   * @return 独立测试结论、实际耗时、数据摘要和可操作的诊断
   */
  @PostMapping("/test")
  public Result<ProviderProbeView> test(@Valid @RequestBody ProviderProbeRequest request) {
    return Result.success(service.test(request));
  }
}
