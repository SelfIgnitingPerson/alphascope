package com.alphascope.market.service;

import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.vo.ProviderProbeView;

/**
 * 由管理员显式触发的通道样本测试，复用实际路由、许可和保护机制。
 *
 * @author chenxuefeng
 */
public interface ProviderProbeService {
  /**
   * 测试一个已配置通道的一项基础能力，不落行情库、不自动降级。
   *
   * @param request 通道、能力及精确测试日期
   * @return 核验成功、失败、保护跳过或超时的独立结论
   */
  ProviderProbeView test(ProviderProbeRequest request);
}
