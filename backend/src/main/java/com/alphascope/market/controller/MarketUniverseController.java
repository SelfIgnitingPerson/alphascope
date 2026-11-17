package com.alphascope.market.controller;

import com.alphascope.common.response.Result;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.market.vo.MarketUniverseView;
import com.alphascope.operations.policy.ResearchData;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只读展示北交所目录的精确日期、成员与归档指纹，刷新页面不采集。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
@RequestMapping("/api/v1/market/universe")
public class MarketUniverseController {
  private final MarketUniverseService service;

  public MarketUniverseController(MarketUniverseService service) {
    this.service = service;
  }

  /**
   * 查询指定上海日期已归档的北交所在市成员；没有该日资料时明确未归档。
   *
   * @param date 清单实际观测日期
   * @return 日期、来源、成员与不可变证据引用
   */
  @GetMapping
  public Result<MarketUniverseView> get(@RequestParam LocalDate date) {
    return Result.success(service.snapshot(date));
  }
}
