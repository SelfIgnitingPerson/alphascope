package com.alphascope.scheduler.dto;

/**
 * 原任务历史的服务端分页条件。
 *
 * @author chenxuefeng
 * @param id 原任务编号
 * @param size 每页大小
 * @param offset 零起点
 */
public record JobHistoryQuery(long id, int size, int offset) {}
