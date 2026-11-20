package com.alphascope.market.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 历史档案修复执行结果，逐日说明缺失的真实材料。
 *
 * @author chenxuefeng
 * @param date 研究截止日
 * @param restored 新核验为完整的归档数
 * @param reused 已完整的原归档数
 * @param issues 仍然无法补齐的原因
 */
public record MarketRepairView(LocalDate date, int restored, int reused, List<String> issues) {}
