package com.alphascope.market.controller;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import com.alphascope.market.dto.InstrumentSearchRequest;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.BarView;
import com.alphascope.market.vo.BarsResponse;
import com.alphascope.market.vo.InstrumentView;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询证券主数据及带明确单位和来源的原价日线。
 *
 * @author chenxuefeng
 */
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {
  private final MarketService service;

  public InstrumentController(MarketService service) {
    this.service = service;
  }

  /**
   * 按代码或名称搜索证券，统一绑定分页查询条件。
   *
   * @param request 关键词 q、页码 page 和分页大小 size，保留原有参数名称及默认值
   * @return 统一响应中的证券分页结果
   */
  @GetMapping
  public Result<PageResponse<InstrumentView>> list(
      @Valid @ModelAttribute @ParameterObject InstrumentSearchRequest request) {
    return Result.success(service.instruments(request));
  }

  /**
   * 读取已登记证券的公开主数据。
   *
   * @param symbol URL路径中的统一证券代码
   * @return 证券主数据，未登记时返回404
   */
  @GetMapping("/{symbol}")
  public Result<InstrumentView> detail(@PathVariable String symbol) {
    return Result.success(InstrumentView.from(service.instrument(symbol)));
  }

  /**
   * 读取证券同源原价日线与因子，路径标识与日期条件分别绑定。
   *
   * @param symbol URL 路径中的统一证券代码
   * @param request 必填 start、end 日期闭区间
   * @return 统一响应中的日线及金额、数量单位
   */
  @GetMapping("/{symbol}/bars")
  public Result<BarsResponse> bars(
      @PathVariable String symbol,
      @Valid @ModelAttribute @ParameterObject DateRangeRequest request) {
    return Result.success(
        new BarsResponse(
            service.bars(symbol, request).stream().map(BarView::from).toList(),
            "CNY",
            "SHARE",
            "CNY"));
  }
}
