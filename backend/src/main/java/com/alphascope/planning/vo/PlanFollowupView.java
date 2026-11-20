package com.alphascope.planning.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 本账号计划批量跟踪结果，失败逐项展示并允许重试。
 *
 * @author chenxuefeng
 * @param date 复核业务日
 * @param reviewed 本次成功核对数量
 * @param remaining 尚未处理数量
 * @param issues 单项缺项或失败的可操作说明
 */
public record PlanFollowupView(LocalDate date, int reviewed, int remaining, List<String> issues) {}
