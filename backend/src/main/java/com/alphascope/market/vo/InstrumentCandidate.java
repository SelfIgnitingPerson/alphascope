package com.alphascope.market.vo;

/**
 * 公开证券搜索结果，不接受用户自行提供公司名称作为有效代码证据。
 *
 * @author chenxuefeng
 * @param symbol 标准证券代码
 * @param name 来源中的证券名称
 * @param exchange SH、SZ或BJ
 * @param registered 是否已登记主数据
 * @param source 查验来源
 */
public record InstrumentCandidate(
    String symbol, String name, String exchange, boolean registered, String source) {}
