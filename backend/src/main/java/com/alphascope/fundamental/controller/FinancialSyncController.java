package com.alphascope.fundamental.controller;

import com.alphascope.common.response.Result;
import com.alphascope.common.vo.CollectionView;
import com.alphascope.fundamental.dto.FinancialSyncInput;
import com.alphascope.fundamental.service.FinancialSyncService;
import com.alphascope.operations.policy.OperationalData;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员显式补充公开三表材料，返回实际采集状态和缓存原因。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
public class FinancialSyncController {
  private final FinancialSyncService service;

  public FinancialSyncController(FinancialSyncService service) {
    this.service = service;
  }

  /**
   * 同步单个证券并返回真实状态，失败保留原材料。
   *
   * @param input 证券及截止日
   * @return 来源实际结果，不将部分报表可用解释成三表齐全
   */
  @PostMapping("/api/v1/admin/financials/sync")
  public Result<CollectionView> sync(@Valid @RequestBody FinancialSyncInput input) {
    if (input.date().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("财报同步研究日不能为未来");
    service.sync(input.symbol(), input.date());
    return Result.success(service.status(input.symbol(), input.date()));
  }
}
