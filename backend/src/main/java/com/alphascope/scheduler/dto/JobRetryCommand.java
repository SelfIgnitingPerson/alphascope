package com.alphascope.scheduler.dto;

/**
 * 原任务重新入队的服务端命令，不能由客户端指定执行状态。
 *
 * @author chenxuefeng
 * @param id 原任务编号
 * @param attempt 新执行序号
 * @param dataScope 当前来源配置
 * @param inputHash 当前输入指纹
 * @param reservationBytes 本次预算预留
 */
public record JobRetryCommand(
    long id, int attempt, String dataScope, String inputHash, long reservationBytes) {}
