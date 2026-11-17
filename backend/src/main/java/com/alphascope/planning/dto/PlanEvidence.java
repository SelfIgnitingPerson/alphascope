package com.alphascope.planning.dto;

import com.alphascope.intelligence.vo.HoldingResearchView;
import java.math.BigDecimal;

/**
 * 原研究与保存时的价格尺度一起冻结，后补因子不得覆盖原锚点。
 *
 * @author chenxuefeng
 * @param research 保存时完整研究
 * @param anchorFactor 保存当日同源正数复权因子
 */
public record PlanEvidence(HoldingResearchView research, BigDecimal anchorFactor) {}
