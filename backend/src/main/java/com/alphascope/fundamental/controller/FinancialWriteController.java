package com.alphascope.fundamental.controller;

import com.alphascope.common.response.Result;
import com.alphascope.fundamental.dto.FinancialInput;
import com.alphascope.fundamental.service.FinancialWriteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登记带公开来源的手工累计财报；控制器只负责校验请求并交给业务层。
 *
 * @author chenxuefeng
 */
@RestController
public class FinancialWriteController {
  private final FinancialWriteService service;

  public FinancialWriteController(FinancialWriteService service) {
    this.service = service;
  }

  /**
   * 新增手工累计财报版本，不覆盖已保存证据。
   *
   * @param input 附公开来源和版本序号的累计财报
   * @return 保存成功的统一空响应
   */
  @PostMapping("/api/v1/admin/financials")
  public Result<Void> insert(@Valid @RequestBody FinancialInput input) {
    service.insert(input);
    return Result.success();
  }
}
