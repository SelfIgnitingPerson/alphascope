package com.alphascope.industry.controller;

import com.alphascope.common.response.Result;
import com.alphascope.industry.dto.ThemeLeaderUpdateInput;
import com.alphascope.industry.service.ThemeLeadersService;
import com.alphascope.industry.vo.ThemeLeaderRevisionView;
import com.alphascope.operations.policy.OperationalData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员维护产业核心目录，不更新历史样本或历史报告。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1/admin/themes")
public class ThemeLeaderAdminController {
  private final ThemeLeadersService service;

  public ThemeLeaderAdminController(ThemeLeadersService service) {
    this.service = service;
  }

  /**
   * 读取当前修订供维护，公共目录仍走原只读接口。
   *
   * @param code 主题代码
   * @return 当前修订和时效
   */
  @GetMapping("/{code}/leaders")
  public Result<ThemeLeaderRevisionView> read(@PathVariable String code) {
    return Result.success(service.revision(code));
  }

  /**
   * 显式追加目录修订。
   *
   * @param code 主题代码
   * @param input 预期修订及新目录
   * @return 保存后的新修订
   */
  @PostMapping("/{code}/leaders")
  public Result<ThemeLeaderRevisionView> save(
      @PathVariable String code, @Valid @RequestBody ThemeLeaderUpdateInput input) {
    return Result.success(service.revise(code, input));
  }
}
