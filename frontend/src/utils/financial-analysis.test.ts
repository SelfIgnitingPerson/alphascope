import { expect, it } from 'vitest';
import { analyzeFinancialReports, financialNumber } from './financial-analysis';
import type { Financial } from '../types/fundamental';
function report(year: number, values: Partial<Financial> = {}): Financial {
  return {
    period_end: `${year}-09-30`,
    published_at: `${year}-10-25T18:00:00`,
    report_type: 'Q3_CUMULATIVE',
    source: 'TEST',
    revenue: 100,
    net_profit: 10,
    operating_cashflow: 8,
    gross_margin: 0.3,
    ...values,
  };
}
it('compares the same cumulative period and reproduces the mixed picture from the supplied financials', () => {
  const data = analyzeFinancialReports({
    items: [
      report(2026, {
        revenue: 4949713570.12,
        net_profit: 368659322.16,
        operating_cashflow: 213987208.18,
        gross_margin: 0.26066106,
      }),
      report(2025, {
        revenue: 4026030778.35,
        net_profit: 399069661.11,
        operating_cashflow: -319034472.29,
        gross_margin: 0.27319892,
      }),
      report(2025, {
        period_end: '2025-12-31',
        report_type: 'ANNUAL',
        published_at: '2026-03-20T18:00:00',
        revenue: 6067759091.55,
      }),
    ],
    date: '2026-11-17',
  });
  expect(data.type).toBe('Q3_CUMULATIVE');
  expect(data.previous?.period_end).toBe('2025-09-30');
  expect(data.title).toContain('经营表现分化');
  expect(data.metrics[0]!.growth).toBeCloseTo(0.229427, 4);
  expect(data.metrics[1]!.growth).toBeCloseTo(-0.076203, 4);
  expect(data.metrics[2]!.growth).toBeNull();
  expect(data.metrics[2]!.change).toContain('净流出转为净流入');
  expect(data.metrics[3]!.difference).toBeCloseTo(-0.01253786, 8);
  expect(data.positives.map((m) => m.key)).toEqual(['revenue', 'operating_cashflow']);
  expect(data.negatives.map((m) => m.key)).toEqual(['net_profit', 'gross_margin']);
  expect(data.followUps.join('')).toContain('增收未增利');
  expect(data.followUps.join('')).toContain('现有字段不能确认具体原因');
});
it('does not turn missing, empty or invalid numbers into zero, while preserving real zero', () => {
  for (const value of [null, undefined, '', ' ', Infinity, 'nan', false])
    expect(financialNumber(value)).toBeNull();
  expect(financialNumber('0')).toBe(0);
  const data = analyzeFinancialReports({
    items: [report(2025, { revenue: 0 }), report(2026, { net_profit: null })],
    date: '2026-11-17',
  });
  expect(data.metrics[0]!.growth).toBeNull();
  expect(data.metrics[1]!.tone).toBe('unknown');
  expect(data.metrics[1]!.current).toBeNull();
});
it('labels turnaround and narrowing losses instead of inventing growth rates from negative bases', () => {
  const turned = analyzeFinancialReports({
    items: [report(2025, { net_profit: -10 }), report(2026, { net_profit: 5 })],
    date: '2026-11-17',
  });
  expect(turned.metrics[1]!.growth).toBeNull();
  expect(turned.metrics[1]!.change).toBe('同比扭亏为盈');
  const narrowed = analyzeFinancialReports({
    items: [report(2025, { net_profit: -10, revenue: 90 }), report(2026, { net_profit: -5 })],
    date: '2026-11-17',
  });
  expect(narrowed.metrics[1]!.change).toContain('仍未盈利');
  expect(narrowed.title).toContain('分化');
});
it('does not compare different sources or fill missing historical years with zero', () => {
  const different = analyzeFinancialReports({
    items: [report(2025, { source: 'OTHER' }), report(2026)],
    date: '2026-11-17',
  });
  expect(different.comparable).toBe(false);
  expect(different.metrics.every((m) => m.previous === null)).toBe(true);
  expect(different.history[0]!.comparableSource).toBe(false);
  const gap = analyzeFinancialReports({ items: [report(2024), report(2026)], date: '2026-11-17' });
  expect(gap.previous).toBeUndefined();
  expect(gap.history.map((p) => p.year)).toEqual([2024, 2025, 2026]);
  expect(gap.history[1]!.row).toBeNull();
});
it('keeps future disclosures, inconsistent report types and ambiguous duplicate periods out of comparisons', () => {
  const data = analyzeFinancialReports({
    items: [
      report(2025),
      report(2026, { published_at: '2026-11-19T18:00:00' }),
      report(2026, { report_type: 'H1_CUMULATIVE' }),
    ],
    date: '2026-11-17',
  });
  expect(data.latest?.period_end).toBe('2025-09-30');
  expect(data.excluded).toBe(2);
  const duplicate = analyzeFinancialReports({
    items: [report(2026), report(2026)],
    date: '2026-11-17',
  });
  expect(duplicate.latest).toBeUndefined();
  expect(duplicate.excluded).toBe(2);
});
it('requires the immediately preceding comparable year, not any earlier saved year', () => {
  const data = analyzeFinancialReports({ items: [report(2010), report(2026)], date: '2026-11-17' });
  expect(data.comparable).toBe(false);
  expect(data.history).toHaveLength(10);
  expect(data.history[0]!.year).toBe(2017);
});
