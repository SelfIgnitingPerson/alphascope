package com.alphascope.intelligence.service;

import com.alphascope.intelligence.dto.ForwardQuery;
import com.alphascope.intelligence.vo.ForwardValidationView;
import com.alphascope.intelligence.vo.ObservationCaptureView;
import java.time.LocalDate;

/**
 * 真正形成信号后的窗口试算与冻结结果读取。
 *
 * @author chenxuefeng
 */
public interface ForwardValidationService {
  /**
   * 仅用已保存实际行情评估成熟窗口并冻结，不采集或下单。
   *
   * @param date 截止日
   * @return 新增窗口及缺项
   */
  ObservationCaptureView evaluate(LocalDate date);

  /**
   * 累计本人全部已读范围的非重叠窗口，不按前端页数统计。
   *
   * @param query 明确范围
   * @return 按版本分组的累计
   */
  ForwardValidationView summary(ForwardQuery query);

  /**
   * 导出本人窗口的完整冻结复算输入及结果，先验证指纹。
   *
   * @param id 窗口编号
   * @return 不可变完整证据
   */
  com.alphascope.intelligence.vo.ForwardEvidenceView evidence(long id);
}
