package com.alphascope.intelligence.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日观察的双时点读取范围。
 *
 * @author chenxuefeng
 * @param symbol 证券，批量读取可为空
 * @param date 截止业务日
 * @param cutoff 实际可见截止时点
 * @param createdAfter 计划保存时刻，普通查询为空
 * @param beforeId 分页游标，首批为空
 */
public record ObservationQuery(
    String symbol,
    LocalDate date,
    LocalDateTime cutoff,
    LocalDateTime createdAfter,
    Long beforeId) {
  public ObservationQuery(String symbol, LocalDate date, LocalDateTime cutoff) {
    this(symbol, date, cutoff, null, null);
  }
}
