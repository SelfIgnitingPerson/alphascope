package com.alphascope.planning.vo;

import com.alphascope.intelligence.vo.HoldingResearchView;
import java.time.LocalDateTime;

/**
 * 计划原始研究保持冻结，归档只影响当前跟踪列表。
 *
 * @author chenxuefeng
 * @param id 计划主键
 * @param horizon 保存周期
 * @param actionCode 保存行动
 * @param archived 用户已归档状态
 * @param createdAt 实际保存时刻
 * @param snapshotHash 证据文本指纹
 * @param research 原始研究和价位，不替换为当前结果
 */
public record PlanView(
    long id,
    String horizon,
    String actionCode,
    boolean archived,
    LocalDateTime createdAt,
    String snapshotHash,
    HoldingResearchView research) {}
