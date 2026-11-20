package com.alphascope.portfolio.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 同时限制业务日与实际记录时点，后补登记不冒充历史持仓。
 *
 * @author chenxuefeng
 * @param asOf 业务截止日
 * @param cutoff 当时可见的记录时点
 */
public record PortfolioHistoryQuery(LocalDate asOf, LocalDateTime cutoff) {}
