package com.alphascope.market.dto;

import java.util.Map;
import lombok.Builder;

/**
 * Tushare官方列协议请求，动态参数仅在供应商边界使用。
 *
 * @author chenxuefeng
 */
@Builder
public record TushareRequest(
    /** 已知官方接口名，不接受HTTP用户直接传入。 */
    String api,
    /** 供应商参数，如日期、证券代码及分页；不得包含Token。 */
    Map<String, ?> parameters,
    /** 明确请求的输出列，空值表示供应商默认字段。 */
    String fields) {}
