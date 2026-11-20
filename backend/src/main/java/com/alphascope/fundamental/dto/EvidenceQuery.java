package com.alphascope.fundamental.dto;

import java.time.LocalDate;

/**
 * 有明确时点的证据读取条件，租户由会话注入。
 *
 * @author chenxuefeng
 * @param symbol 证券代码
 * @param asOf 截止研究日期
 */
public record EvidenceQuery(String symbol, LocalDate asOf) {}
