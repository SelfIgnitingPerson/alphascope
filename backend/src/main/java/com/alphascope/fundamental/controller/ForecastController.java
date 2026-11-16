package com.alphascope.fundamental.controller;

import com.alphascope.common.response.Result;
import com.alphascope.fundamental.service.ForecastService;
import com.alphascope.fundamental.vo.ForecastView;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只读查询实际观测到的公开预测快照；页面刷新不触发上游采集。
 *
 * @author chenxuefeng
 */
@RestController
@RequestMapping("/api/v1/expectations")
public class ForecastController {
  private final ForecastService service;

  public ForecastController(ForecastService service) {
    this.service = service;
  }

  /**
   * 只读查询已经保存的机构预测快照，不触发采集。
   *
   * @param asOf 可选截止日期，省略时采用上海当前日期
   * @return 业务时点可见的预测列表及来源边界
   */
  @GetMapping
  public Result<List<ForecastView>> list(@RequestParam(required = false) LocalDate asOf) {
    return Result.success(
        service.list(asOf == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : asOf));
  }
}
