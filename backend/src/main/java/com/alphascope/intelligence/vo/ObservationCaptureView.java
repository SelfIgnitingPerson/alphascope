package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 显式留档的执行结果，失败逐项说明，不以数量证明策略有效。
 *
 * @author chenxuefeng
 * @param date 研究日
 * @param saved 新增版本数
 * @param reused 原内容已保存数量
 * @param issues 失败与缺项
 */
public record ObservationCaptureView(
    LocalDate date, int saved, int reused, List<String> issues, String captureKind) {
  /** 保留执行类结果构造，明确非每日判断分类。 */
  public ObservationCaptureView(LocalDate date, int saved, int reused, List<String> issues) {
    this(date, saved, reused, issues, null);
  }
}
