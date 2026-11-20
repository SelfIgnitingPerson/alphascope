package com.alphascope.backtest.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 冻结共用资金回放的本金、研究区间及完整证券范围。
 *
 * @author chenxuefeng
 */
public record PortfolioReplaySettings(
    LocalDate start, LocalDate end, double capital, List<String> symbols) {}
