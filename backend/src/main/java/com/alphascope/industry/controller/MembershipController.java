package com.alphascope.industry.controller;

import com.alphascope.common.response.Result;
import com.alphascope.industry.dto.MembershipInput;
import com.alphascope.industry.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工登记主题归属的真实有效日期，不倒填历史成分。
 *
 * @author chenxuefeng
 */
@RestController
public class MembershipController {
  private final MembershipService service;

  public MembershipController(MembershipService service) {
    this.service = service;
  }

  /**
   * 登记人工主题归属的实际生效区间，不推造历史成员。
   *
   * @param input 证券、主题及真实生效和失效日期
   * @return 登记成功的统一空响应
   */
  @PostMapping("/api/v1/admin/theme-memberships")
  public Result<Void> add(@Valid @RequestBody MembershipInput input) {
    service.add(input);
    return Result.success();
  }
}
