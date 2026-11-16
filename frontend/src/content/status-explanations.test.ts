import { expect, it } from 'vitest';
import {
  holdingStatusExplanation,
  qualityStatusExplanation,
  channelStatusExplanation,
} from './status-explanations';
import type { HoldingResearch, HoldingHorizon, HoldingAction } from '../types/holding-research';
import type { Job, JobStage } from '../types/jobs';

const horizon: HoldingHorizon = {
  code: 'SHORT',
  title: '短期交易',
  period: '1–20个交易日',
  status: 'UNAVAILABLE',
  conclusion: '暂停',
  actions: [],
  evidence: [],
  missing: ['缺少当日有效行情'],
};
const research = {
  name: '英维克',
  asOf: '2026-11-13',
  quoteDate: '2026-11-12',
  source: null,
} as HoldingResearch;
it('uses known date gaps without inventing a supplier restriction or auto-selecting another day', () => {
  const result = holdingStatusExplanation({ research, horizon });
  expect(result.reason).toContain('2026-11-13');
  expect(result.reason).toContain('2026-11-12');
  expect(result.missing).toEqual(['缺少当日有效行情']);
  expect(result.action).toContain('不会自动替换');
  expect(result.next?.to).toBe('/jobs');
  const unknown = holdingStatusExplanation({ research: { ...research, quoteDate: null }, horizon });
  expect(unknown.reason).toBe('暂停');
  expect(unknown.facts).toContain('最近已保存行情日：尚无记录');
  const suspended = holdingStatusExplanation({
    research: {
      ...research,
      priceVolume: { status: 'SUSPENDED' } as HoldingResearch['priceVolume'],
    },
    horizon,
  });
  expect(suspended.reason).toContain('已确认停牌');
  expect(suspended.action).toContain('不应补造日K');
  const demo = holdingStatusExplanation({
    research: { ...research, priceVolume: { status: 'DEMO' } as HoldingResearch['priceVolume'] },
    horizon,
  });
  expect(demo.reason).toContain('演示资料');
  expect(demo.next?.to).toBe('/channels');
});
it('distinguishes an unmet condition from a genuinely missing input', () => {
  const action = {
    title: '回踩建仓',
    status: 'WAIT',
    conclusion: '等待量价确认',
    conditions: [
      { label: '量能', met: null, observed: '未取得', required: '量比≤1' },
      { label: '价格', met: false, observed: '120', required: '110—112' },
    ],
  } as HoldingAction;
  const result = holdingStatusExplanation({
    research,
    horizon: { ...horizon, status: 'WAIT', missing: [] },
    action,
  });
  expect(result.missing).toEqual(['量能：未取得；要求量比≤1']);
  expect(result.facts?.some((item) => item.includes('未满足：价格') && item.includes('120'))).toBe(
    true,
  );
  expect(result.reason).toBe('等待量价确认');
});
it('does not attribute a failed task to a successful stage and treats zero-denominator checks honestly', () => {
  const diagnostic = {
    code: 'NETWORK_TIMEOUT',
    title: '超时',
    reason: '行情读取超时',
    impact: '未完成',
    action: '检查网络',
  };
  const job = { id: 9, business_date: '2026-11-13', diagnostic } as Job;
  const stage = {
    stage_name: 'CALENDAR',
    status: 'SUCCESS',
    details: {
      quality: { status: 'AVAILABLE', available: 1, expected: 1, note: '日历已核验', issues: [] },
    },
  } as JobStage;
  expect(qualityStatusExplanation({ job, stage }).diagnostic).toBeNull();
  const failed = {
    ...stage,
    stage_name: 'MARKET_SYNC',
    status: 'FAILED',
    details: {
      quality: {
        status: 'UNAVAILABLE',
        available: 0,
        expected: 0,
        note: '未完成',
        issues: [{ scope: '002837.SZ', reason: '缺当日日线', status: 'FAILED' }],
      },
    },
  };
  const result = qualityStatusExplanation({ job, stage: failed });
  expect(result.diagnostic?.code).toBe('NETWORK_TIMEOUT');
  expect(result.facts?.some((item) => item.includes('不能判为核验通过'))).toBe(true);
  expect(result.missing).toEqual(['002837.SZ：缺当日日线']);
});
it('preserves the actual channel diagnosis and recorded times without probing', () => {
  const result = channelStatusExplanation({
    channel: 'BAOSTOCK',
    capability: {
      capability: 'STOCK_DAILY',
      supported: true,
      state: 'ACCESS_RESTRICTED',
      lastSuccess: null,
      diagnostic: {
        code: 'BAOSTOCK_API_ERROR_10001011',
        title: '黑名单用户',
        reason: '已有拒绝记录',
        impact: '暂停',
        action: '核对官网',
        providerError: {
          provider: 'BAOSTOCK',
          code: '10001011',
          meaning: '黑名单用户',
          message: null,
          messageState: 'NOT_RECORDED',
        },
      },
    },
  });
  expect(result.subject).toContain('BaoStock · 个股日线');
  expect(result.diagnostic?.providerError?.code).toBe('10001011');
  expect(result.facts).toEqual(['最近成功：—']);
});
