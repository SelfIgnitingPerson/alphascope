package com.alphascope.identity.controller;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import com.alphascope.identity.dto.UserAdminInput;
import com.alphascope.identity.service.UserAdminService;
import com.alphascope.identity.vo.UserAdminView;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员账号支持入口，所有动作先经过管理员角色验证。
 *
 * @author chenxuefeng
 */
@RestController
public class UserAdminController {
  private final UserAdminService service;

  public UserAdminController(UserAdminService service) {
    this.service = service;
  }

  /**
   * 查看账号启用状态。
   *
   * @param page 分页字段
   * @return 非敏感账号资料
   */
  @GetMapping("/api/v1/admin/users")
  public Result<PageResponse<UserAdminView>> users(
      @Valid @ModelAttribute @ParameterObject PageRequest page) {
    return Result.success(service.page(page));
  }

  /**
   * 重置密码或启停普通账号并撤销旧会话。
   *
   * @param id 账号主键
   * @param input 可空密码及状态
   * @return 成功响应
   */
  @PutMapping("/api/v1/admin/users/{id}")
  public Result<Void> update(@PathVariable long id, @Valid @RequestBody UserAdminInput input) {
    service.update(id, input);
    return Result.success();
  }
}
