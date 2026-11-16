package com.alphascope.backtest.dto;

/**
 * 实验元数据分页的服务端查询条件。
 *
 * @author chenxuefeng
 */
public record ExperimentPageQuery(
    /** 单页数量。 */
    int size,
    /** 零基偏移量。 */
    int offset) {}
