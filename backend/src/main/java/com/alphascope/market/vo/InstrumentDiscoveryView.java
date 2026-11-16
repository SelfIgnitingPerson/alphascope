package com.alphascope.market.vo;

import java.util.List;

/**
 * 本地与远端搜索合并结果；远端失败不隐藏已登记证券。
 *
 * @author chenxuefeng
 * @param items 去重的股票候选
 * @param remoteStatus AVAILABLE或UNAVAILABLE
 * @param message 可操作的来源说明
 */
public record InstrumentDiscoveryView(
    List<InstrumentCandidate> items, String remoteStatus, String message) {}
