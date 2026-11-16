package com.alphascope.market.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * 具有明确目录及价格来源的市场截面；独立目录须保留观测时刻，不倒填历史。
 *
 * @author chenxuefeng
 */
@Builder
public record ResearchMarketSnapshot(
    /** 实际供应商代码，不能写成MULTI。 */
    String source,
    /** 截面业务日期。 */
    LocalDate date,
    /** 包含code和tradeStatus的供应商证券目录；UNKNOWN表示状态尚未核验。 */
    List<Map<String, String>> universe,
    /** 标准化原价、前收盘、人民币成交额和明确停牌状态。 */
    List<Map<String, String>> daily,
    /** 原始供应商响应，便于核对标准化过程；可以为空但不能伪造。 */
    String upstreamJson,
    /** 当前来源特有的统计范围、目录和缺失状态限制。 */
    List<String> limitations) {}
