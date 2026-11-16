package com.alphascope.portfolio.controller;

import com.alphascope.common.response.Result;
import com.alphascope.portfolio.dto.WatchlistInput;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.WatchlistView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自选股协议入口；持仓协议由PortfolioController独立维护。
 *
 * @author chenxuefeng
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {
  private final PortfolioService service;

  public WatchlistController(PortfolioService service) {
    this.service = service;
  }

  /**
   * 读取当前自选研究范围。
   *
   * @return 启用的关注标的列表
   */
  @GetMapping
  public Result<List<WatchlistView>> list() {
    return Result.success(service.watchlistView());
  }

  /**
   * 添加或恢复启用已登记的关注标的。
   *
   * @param input 统一证券代码及人工研究优先级
   * @return 保存成功的统一空响应
   */
  @PostMapping
  public Result<Void> add(@Valid @RequestBody WatchlistInput input) {
    service.watch(input.symbol(), input.priority());
    return Result.success();
  }

  /**
   * 删除当前关注关系，保留已经冻结的历史报告。
   *
   * @param symbol URL路径中的统一证券代码
   * @return 删除成功的统一空响应
   */
  @DeleteMapping("/{symbol}")
  public Result<Void> remove(@PathVariable String symbol) {
    service.unwatch(symbol);
    return Result.success();
  }
}
