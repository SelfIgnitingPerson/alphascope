package com.alphascope.market.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 市场观察引用的原始快照；保持旧证据的字段名称、空字段省略和排序口径。
 *
 * @author chenxuefeng
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record MarketSnapshotReference(
    /** 截面快照对应的交易日期；因子快照无此字段。 */
    LocalDate date,
    /** 被引用快照内容的 SHA-256 指纹。 */
    String hash,
    /** 被引用快照的数据库主键。 */
    Long id,
    /** 因子快照对应的供应商证券代码；截面快照无此字段。 */
    String symbol,
    /** 快照类型：market_breadth_snapshot 或 market_factor_snapshot。 */
    String type,
    /** 与原价及该快照一致的实际来源；旧证据可为空。 */
    String source) {}
