package com.alphascope.planning.dto;

/**
 * 由服务端路径及已校验分页构建的复核查询。
 *
 * @author chenxuefeng
 * @param planId 已确认归属的计划
 * @param size 每页条数
 * @param offset 零起点偏移
 */
public record PlanReviewQuery(long planId, int size, int offset) {}
