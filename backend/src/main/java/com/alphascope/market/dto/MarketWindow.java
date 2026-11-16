package com.alphascope.market.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 行情和供应商确认的缺席证据一起返回；未返回K线不等于停牌。
 *
 * @author chenxuefeng
 */
public record MarketWindow(
    /** 同一供应商、同一真实或演示模式下的行情窗口。 */
    List<DailyBarDto> bars,

    /** 供应商明确证实的停牌日期与原因；未知缺口仍须使采集失败。 */
    Map<LocalDate, String> absences) {}
