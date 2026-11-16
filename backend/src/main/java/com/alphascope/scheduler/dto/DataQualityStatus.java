package com.alphascope.scheduler.dto;

/**
 * 数据质量独立于方法是否返回、阶段是否执行成功，不把缺失值当作完整证据。
 *
 * @author chenxuefeng
 */
public enum DataQualityStatus {
  /** 所需证据满足本阶段明确的覆盖要求。 */
  AVAILABLE,
  /** 有部分证据可用，仍存在缺项。 */
  PARTIAL,
  /** 无法取得所需证据。 */
  UNAVAILABLE,
  /** 当前模式或业务范围不适用。 */
  NOT_APPLICABLE,
  /** 当前时刻尚未到供应商数据可用时间。 */
  NOT_READY,
  /** 尚未检查或旧任务没有记录。 */
  UNKNOWN
}
