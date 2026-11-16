package com.alphascope.market.provider;

/**
 * 单个证券或日期缺少数据，不等同于整个供应商能力故障，不触发全通道冷却。
 *
 * @author chenxuefeng
 */
public final class ProviderDataUnavailableException extends RuntimeException {
  public ProviderDataUnavailableException(String code) {
    super(code);
  }
}
