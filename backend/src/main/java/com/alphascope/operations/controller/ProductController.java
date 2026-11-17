package com.alphascope.operations.controller;

import com.alphascope.common.response.Result;
import com.alphascope.operations.dto.SourceLicenseInput;
import com.alphascope.operations.policy.OperationalData;
import com.alphascope.operations.service.ProductStatusService;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.operations.vo.ProductStatusView;
import com.alphascope.operations.vo.WorkspaceStatusView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户运行就绪与管理员许可登记；后台安全过滤分别校验角色。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
public class ProductController {
  private final ProductStatusService status;
  private final SourceLicenseService licenses;

  public ProductController(ProductStatusService status, SourceLicenseService licenses) {
    this.status = status;
    this.licenses = licenses;
  }

  /**
   * 查看当前账号应有日期和资源。
   *
   * @return 不含其他用户资料的状态
   */
  @GetMapping("/api/v1/workspace/status")
  public Result<WorkspaceStatusView> workspace() {
    return Result.success(status.workspace());
  }

  /**
   * 管理员核对发布缺项和许可登记。
   *
   * @return 管理状态
   */
  @GetMapping("/api/v1/admin/product/status")
  public Result<ProductStatusView> product() {
    return Result.success(status.product());
  }

  /**
   * 新增许可修订，依据必须由管理员实际核验。
   *
   * @param input 来源、授权范围、依据与日期
   * @return 登记成功
   */
  @PostMapping("/api/v1/admin/product/licenses")
  public Result<Void> declare(@Valid @RequestBody SourceLicenseInput input) {
    licenses.declare(input);
    return Result.success();
  }
}
