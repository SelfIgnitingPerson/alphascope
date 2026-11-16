package com.alphascope.operations.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 对运维接收方发布的最小状态事件，不包含私人自选、持仓或报告正文。
 *
 * @author chenxuefeng
 * @param workspace 数字工作区标识，便于部署方定位
 * @param codes 受控状态代码
 * @param state ATTENTION或RECOVERED
 * @param checkedAt 观察时刻
 */
public record RuntimeAlertEvent(
    long workspace, Set<String> codes, String state, Instant checkedAt) {}
