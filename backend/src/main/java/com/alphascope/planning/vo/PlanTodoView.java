package com.alphascope.planning.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 当前账号全部计划的研究待办，不以当前分页代替全局风险数量。
 *
 * @author chenxuefeng
 * @param date 截止业务日
 * @param reviewed 当日具备有效复核结果的数量，不受已阅读标记影响
 * @param total 跟踪中计划总数
 * @param risk 原风险线已触及且未确认阅读的数量
 * @param changed 方案或口径改变且未确认阅读的数量
 * @param data 证据不足且未确认阅读的数量
 * @param due 尚无当日复核的数量
 * @param acknowledged 已确认阅读当前证据的数量
 * @param items 全局排序后的前五个待办
 */
public record PlanTodoView(
    LocalDate date,
    int total,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        int reviewed,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        int risk,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        int changed,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        int data,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        int due,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        int acknowledged,
    List<PlanSummary> items,
    java.util.Set<String> inputSources) {}
