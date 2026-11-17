package com.alphascope.market.dto;

import com.alphascope.market.calculator.MarketObservationCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * 同日期、同来源的分项计算输入；scope不能从样本大小推断。
 *
 * @author chenxuefeng
 * @param date 业务日期
 * @param calendar 已核验日历
 * @param snapshots 不可变截面
 * @param factors 同源已核验因子
 * @param benchmark 基准原价与因子
 * @param parameters 固定阈值
 * @param scope 明确范围
 */
public record MarketObservationInput(
    LocalDate date,
    List<LocalDate> calendar,
    Map<LocalDate, JsonNode> snapshots,
    Map<String, NavigableMap<LocalDate, Double>> factors,
    JsonNode benchmark,
    MarketObservationCalculator.Parameters parameters,
    String scope) {}
