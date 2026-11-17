package com.alphascope.market.dto;

/**
 * 供应商当日证券清单成员；不携带推断的历史上市日或交易状态。
 *
 * @author chenxuefeng
 * @param symbol 六位代码及交易所后缀
 * @param name 目录实际返回的证券简称
 */
public record UniverseMember(String symbol, String name) {}
