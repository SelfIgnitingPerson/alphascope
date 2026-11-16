import { api, write } from './client';
import type { ProviderProbeRequest, ProviderProbeResult } from '../types/provider-probe';
/** 显式管理员操作；最多等待20秒后台测试及响应余量，不由刷新或打开页面触发。 */
export const testProvider = (input: ProviderProbeRequest) =>
  api<ProviderProbeResult>('/admin/market/providers/test', {
    ...write('POST', input),
    timeoutMs: 30000,
  });
