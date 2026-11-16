package com.alphascope.market.dto;

/**
 * 参与任务输入指纹的行情缓存配置；字段名保持既有冻结证据格式。
 *
 * @author chenxuefeng
 */
public record MarketCacheSettings(
    /** 完整缓存可复用分钟数；非正值禁用缓存复用。 */
    long cacheMinutes,
    /** 已存真实历史复用的策略标识，变更时须重新核对幂等语义。 */
    String historicalReplay) {}
