package com.alphascope.common.validation;

import java.time.LocalDate;

/**
 * 共享日期闭区间规则，供请求约束和非 HTTP 业务调用采用同一判断口径。
 *
 * @author chenxuefeng
 */
public final class DateRangeValidator {
  private DateRangeValidator() {}

  /**
   * 校验已提供的日期顺序；可选日期的缺省值由业务服务确定。
   *
   * @param start 可空的起始日期，包含当日
   * @param end 可空的结束日期，包含当日
   * @return 任一日期为空或起始日期不晚于结束日期时为 true
   */
  public static boolean isOrdered(LocalDate start, LocalDate end) {
    return start == null || end == null || !start.isAfter(end);
  }

  /**
   * 要求完整且有序的日期区间；内部调用不会经过 Controller 的 Bean Validation。
   *
   * @param start 必填起始日期，包含当日
   * @param end 必填结束日期，包含当日
   * @throws IllegalArgumentException 日期缺失或起始日期晚于结束日期
   */
  public static void requireValid(LocalDate start, LocalDate end) {
    if (start == null || end == null) {
      throw new IllegalArgumentException("开始日期和结束日期不能为空");
    }
    if (!isOrdered(start, end)) {
      throw new IllegalArgumentException("开始日期不能晚于结束日期");
    }
  }
}
