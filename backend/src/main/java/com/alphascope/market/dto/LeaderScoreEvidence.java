package com.alphascope.market.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 按完整市场历史成交额排序得到的领先样本证据，不以自选名单替代。
 *
 * @author chenxuefeng
 */
public record LeaderScoreEvidence(
    /** 业务日期。 */
    LocalDate businessDate,
    /** AVAILABLE或INSUFFICIENT_DATA。 */
    String status,
    /** 全部当日交易标的的历史覆盖证明。 */
    ScoreCoverage coverage,
    /** 按20日均成交额前10%选出的完整名单。 */
    List<String> leaders,
    /** 名单中站上MA20且20日跑赢沪深300的家数。 */
    int confirmed,
    /** 同日期沪深30020日收益，小数。 */
    Double benchmarkReturn,
    /** 明确的缺项或方法口径。 */
    String reason) {}
