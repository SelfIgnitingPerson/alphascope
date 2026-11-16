package com.alphascope.market.vo;

import com.alphascope.market.dto.ScoreCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 完整市场清单证明和实测覆盖，未知全A分母不由沪深或自选数量替代。
 *
 * @author chenxuefeng
 */
public record UniverseCoverageView(
    /** 核验业务日。 */
    LocalDate businessDate,
    /** VERIFIED、PARTIAL或NOT_AVAILABLE。 */
    String status,
    /** 评分准入所用完整范围及分母。 */
    ScoreCoverage coverage,
    /** 实际读取的来源；无匹配证据时为空。 */
    String source,
    /** 原始不可变截面引用。 */
    Long snapshotId,
    /** 原始截面内容指纹。 */
    String contentHash,
    /** 按SSE、SZSE、BSE分别列出实际清单数量。 */
    Map<String, Integer> exchangeMembers,
    /** 具体缺项或校验失败，不只返回布尔值。 */
    List<String> missing,
    /** 完整清单的公开来源声明。 */
    String sourceUrl) {}
