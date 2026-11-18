import { expect, it } from 'vitest';
import {
  forecastObservation,
  planObservation,
  reportObservation,
  themeObservation,
} from './observation-summaries';
import type { ForecastView, ReportAnalysis, ThemeStrength } from '../types/models';
import type { PlanSummary } from '../api/plans';

const date = '2026-11-17';
function plan(overrides: Partial<PlanSummary> = {}): PlanSummary {
  return {
    id: 1,
    name: '测试',
    symbol: '600000.SH',
    asOf: '2026-11-16',
    horizon: 'SHORT',
    actionCode: 'OPEN_PULLBACK',
    archived: false,
    createdAt: '2026-11-16T17:00:00',
    ...overrides,
  };
}
it('does not use future reviews or archived plans as selected-day risk, and separates missing evidence', () => {
  const future = plan({
    latestReviewDate: '2026-11-18',
    latestReviewStatus: 'ORIGINAL_STOP_BREACHED',
  });
  expect(planObservation({ rows: [future], date }).title).toContain('尚待所选日复核');
  expect(planObservation({ rows: [{ ...future, archived: true }], date }).title).toContain(
    '没有需按所选日',
  );
  const missing = plan({ latestReviewDate: date, latestReviewStatus: 'DATA_INSUFFICIENT' });
  expect(planObservation({ rows: [missing], date }).level).toBe('DATA');
  const breached = { ...missing, latestReviewStatus: 'ORIGINAL_STOP_BREACHED' };
  expect(planObservation({ rows: [missing, breached], date }).level).toBe('FIRST');
});

it('does not label a first forecast snapshot as stable and puts downward revisions first', () => {
  const forecast: ForecastView = {
    symbol: '600000.SH',
    name: '测试',
    status: 'AVAILABLE',
    latest: {
      id: 1,
      availableAt: `${date}T17:00:00`,
      fetchedAt: `${date}T17:00:00`,
      source: 'TEST',
      sourceUrl: 'https://example.com',
      ratingAgencies: 5,
      estimates: [],
    },
    changes: [],
    explanation: '首次观测',
    limitations: [],
    collection: {},
  };
  expect(forecastObservation(forecast).title).toContain('不能判断预期趋势');
  forecast.changes = [2026, 2027, 2028, 2029].map((year) => ({
    year,
    previousEps: 1,
    currentEps: year === 2029 ? 0.8 : 1.1,
    difference: year === 2029 ? -0.2 : 0.1,
    previousObservedAt: '2026-11-16T17:00:00',
    currentObservedAt: `${date}T17:00:00`,
  }));
  const result = forecastObservation(forecast);
  expect(result.level).toBe('IMPORTANT');
  expect(result.facts[1]).toContain('2029年');
  expect(result.facts[0]).toContain(date);
});

it('requires a timely public theme definition and complete coverage; zero relative strength is flat', () => {
  const strength = {
    items: [{ trade_date: date, score: null, rs20: 0, breadth: 0.5, coverage: 1 }],
    members: [{ symbol: '600000.SH' }],
    algorithmVersion: 'TEST',
    cohort: { available_at: '2026-11-16T17:00:00' },
  } as ThemeStrength;
  expect(themeObservation({ strength, date }).title).toContain('持平');
  expect(themeObservation({ strength: { ...strength, cohort: null }, date }).level).toBe('DATA');
  expect(themeObservation({ strength, date: '2026-11-18' }).level).toBe('DATA');
  strength.items[0]!.coverage = 0.5;
  expect(themeObservation({ strength, date }).level).toBe('DATA');
});

it('report priorities only use the report date frozen risk evidence', () => {
  const analysis = {
    stocks: [
      {
        name: '测试',
        symbol: '600000.SH',
        status: 'TRADING',
        asOf: date,
        triggered: 0,
        insufficient: 0,
      },
    ],
    priceVolume: [
      {
        symbol: '600000.SH',
        status: 'AVAILABLE',
        asOf: date,
        stopStatus: 'BREACHED',
        close: 44,
        stopReference: 45,
      },
    ],
    assessments: [],
    checks: [],
    missingData: [],
    benchmarkReturn: 0,
  } as unknown as ReportAnalysis;
  expect(reportObservation({ analysis, date }).level).toBe('FIRST');
  expect(reportObservation({ analysis, date }).facts[0]).toContain('44.00');
  expect(reportObservation({ analysis, date: '2026-11-16' }).level).not.toBe('FIRST');
});
