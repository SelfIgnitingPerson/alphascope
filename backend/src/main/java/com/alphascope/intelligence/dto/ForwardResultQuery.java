package com.alphascope.intelligence.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 读取当前身份已筛选观察的窗口结果。
 *
 * @author chenxuefeng
 * @param cutoff 实际可见截止时间
 * @param observationIds 本次读取的观察编号
 */
public record ForwardResultQuery(LocalDateTime cutoff, List<Long> observationIds) {}
