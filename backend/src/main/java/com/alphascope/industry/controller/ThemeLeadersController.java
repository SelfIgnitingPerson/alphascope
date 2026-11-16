package com.alphascope.industry.controller;

import com.alphascope.common.response.Result;
import com.alphascope.industry.service.ThemeLeadersService;
import com.alphascope.industry.vo.ThemeLeadersView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前产业龙头研究目录查询；与历史披露样本、行情和买卖规则分开。
 *
 * @author chenxuefeng
 */
@RestController
@RequestMapping("/api/v1/themes")
public class ThemeLeadersController {
  private final ThemeLeadersService service;

  public ThemeLeadersController(ThemeLeadersService service) {
    this.service = service;
  }

  /**
   * 列出当前已整理的核心企业组，供产业链总览使用。
   *
   * @return 统一响应中的当前目录
   */
  @GetMapping("/leaders")
  public Result<List<ThemeLeadersView>> list() {
    return Result.success(service.list());
  }

  /**
   * 查询一个主题的当前核心企业与逐项来源。
   *
   * @param code 主题代码
   * @return 统一响应中的当前研究目录，不能倒推历史排名
   */
  @GetMapping("/{code}/leaders")
  public Result<ThemeLeadersView> get(@PathVariable String code) {
    return Result.success(service.get(code));
  }
}
