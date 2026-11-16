package com.alphascope.operations.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 当前账号的研究就绪状态，不包含其他账号、备份路径或合同依据。
 *
 * @author chenxuefeng
 * @param checkedAt 核查时刻
 * @param status HEALTHY或ATTENTION
 * @param expectedTradeDate 权威日历确认的应有收盘日，缺证据为空
 * @param calendarStatus 近期日历是否完整
 * @param instruments 当前自选及基准的真实日期
 * @param capacity 当前用户资源预算使用量
 * @param issues 当前需要采取动作的事项
 */
public record WorkspaceStatusView(
    Instant checkedAt,
    String status,
    LocalDate expectedTradeDate,
    String calendarStatus,
    List<OperationsStatusView.Freshness> instruments,
    ResearchCapacity capacity,
    List<String> issues) {}
