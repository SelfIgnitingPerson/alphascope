package com.alphascope.market.dto;

import java.time.LocalDate;
import lombok.Builder;

/**
 * 指定实际来源的市场快照查询，不从另一通道补齐局部字段。
 *
 * @author chenxuefeng
 */
@Builder
public record ResearchSnapshotQuery(
    /** 精确匹配的业务日期。 */
    LocalDate date,
    /** 配置允许的实际供应商代码。 */
    String source,
    /** 是否需要全量原始JSON；页面统计查询不加载大字段。 */
    boolean loadRaw) {}
