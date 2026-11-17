package com.alphascope.market.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 完成分页与总数核验的北交所当前在市目录，不代表收盘行情或历史目录。
 *
 * @author chenxuefeng
 * @param businessDate 实际采集所在上海日期
 * @param observedAt 完成当前目录核验的时刻
 * @param members 北交所在市成员，已剔除其他市场并核对唯一性
 * @param evidenceJson 全部A股分页、总数、前后确认和真实采集区间
 */
public record MarketUniverseBatch(
    LocalDate businessDate,
    Instant observedAt,
    List<UniverseMember> members,
    String evidenceJson) {}
