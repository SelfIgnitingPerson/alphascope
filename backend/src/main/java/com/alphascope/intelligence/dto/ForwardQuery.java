package com.alphascope.intelligence.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 当前账号前瞻记录的读取范围，不接受规则或费率覆盖。
 *
 * @author chenxuefeng
 * @param asOf 截止研究日
 * @param symbol 可选证券范围
 * @param planId 可选本人计划，按实际保存时点及原方法过滤
 */
public record ForwardQuery(
    @NotNull LocalDate asOf,
    @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol,
    Long planId) {}
