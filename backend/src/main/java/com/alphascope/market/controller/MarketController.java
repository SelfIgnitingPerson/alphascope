package com.alphascope.market.controller;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.response.Result;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.service.ScoreService;
import com.alphascope.market.service.UniverseCoverageService;
import com.alphascope.market.vo.MarketObservation;
import com.alphascope.market.vo.MarketOverviewView;
import com.alphascope.market.vo.MarketScoreView;
import com.alphascope.market.vo.ScopedScoreView;
import com.alphascope.market.vo.UniverseCoverageView;
import com.alphascope.operations.policy.ResearchData;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只读展示已有市场分项观察与评分，缺少完整全A维度时保留UNKNOWN。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
  private final ScoreService service;
  private final UniverseCoverageService coverage;

  public MarketController(ScoreService service, UniverseCoverageService coverage) {
    this.service = service;
    this.coverage = coverage;
  }

  @org.springframework.beans.factory.annotation.Autowired private FullMarketService fullMarket;

  /**
   * 精确读取三所观察，不采集、不使用沪深代理替代。
   *
   * @param date 业务日期
   * @return 全A观察或空
   */
  @GetMapping("/full-observation")
  public Result<MarketObservation> full(@RequestParam LocalDate date) {
    return Result.success(fullMarket.observation(date));
  }

  /**
   * 返回实际清单范围、交易所数量和全A覆盖证明缺项，不触发采集。
   *
   * @param date 精确业务日期
   * @return 完整核验或明确缺口
   */
  @GetMapping("/coverage")
  public Result<UniverseCoverageView> coverage(@RequestParam LocalDate date) {
    return Result.success(coverage.inspect(date));
  }

  /**
   * 读取已保存的市场观察与全市场评分，不在查询中回补。
   *
   * @param date 可选业务日期，省略时采用上海当前日期
   * @return 独立观察、评分、实际来源及覆盖限制
   */
  @GetMapping("/overview")
  public Result<MarketOverviewView> overview(@RequestParam(required = false) LocalDate date) {
    return Result.success(
        service.overview(date == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : date));
  }

  /**
   * 按日期查询对象读取已经保存的市场评分。
   *
   * @param request 必填 start、end 日期闭区间
   * @return 统一响应中的历史评分及缺项证据
   */
  @GetMapping("/scores")
  public Result<List<MarketScoreView>> scores(
      @Valid @ModelAttribute @ParameterObject DateRangeRequest request) {
    return Result.success(service.history(request));
  }

  /**
   * 只读计算五维研究分项，完整全A总分继续受范围门槛约束。
   *
   * @param date 业务日期
   * @return 明确公式、范围和缺项的评分
   */
  @GetMapping("/research-score")
  public Result<ScopedScoreView> research(@RequestParam LocalDate date) {
    return Result.success(service.research(date));
  }
}
