import { expect, it } from 'vitest';
import { channelConclusion, savedChannelEvidence } from './channel-guides';
import type { ProviderChannel } from '../types/market';
it('does not label a partly failing channel healthy because another capability succeeded', () => {
  const value: ProviderChannel = {
    channel: 'EASTMONEY',
    state: 'AVAILABLE',
    lastError: '',
    lastSuccess: '2026-11-12T00:00:00Z',
    retryAt: null,
    consecutiveFailures: 0,
    capabilities: [
      { capability: 'INDEX_DAILY', supported: true, state: 'AVAILABLE' },
      { capability: 'MARKET_SNAPSHOT', supported: true, state: 'ACCESS_COOLDOWN' },
      { capability: 'STOCK_DAILY', supported: false, state: 'UNSUPPORTED' },
    ],
  };
  expect(channelConclusion(value)).toContain('访问冷却');
  value.capabilities![1]!.state = 'NOT_CHECKED';
  expect(channelConclusion(value)).toContain('其他能力仍须分别核对');
});
it('keeps saved source evidence independent and does not treat similar names as the same source', () => {
  const rows = [
    {
      symbol: '000300.SH',
      source: 'EASTMONEY',
      trade_date: '2026-11-12',
      fetched_at: '2026-11-12T16:11:00',
    },
    {
      symbol: '002837.SZ',
      source: 'SINA_OTHER',
      trade_date: '2026-11-11',
      fetched_at: '2026-11-12T17:11:00',
    },
    {
      symbol: '002837.SZ',
      source: 'SINATEST',
      trade_date: '2026-11-13',
      fetched_at: '2026-11-13T17:11:00',
    },
  ];
  expect(savedChannelEvidence('SINA', rows)).toEqual({
    count: 1,
    latestDate: '2026-11-11',
    latestFetched: '2026-11-12T17:11:00',
  });
  expect(savedChannelEvidence('BAOSTOCK', rows).latestDate).toBeNull();
});
