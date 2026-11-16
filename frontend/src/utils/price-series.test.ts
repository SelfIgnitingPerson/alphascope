import { expect, it } from 'vitest';
import { chartPrices, comparableReturn } from './price-series';
import type { DailyBar, Indicator } from '../types/models';
const bar = (date: string, factor: DailyBar['factor'] = '1'): DailyBar => ({
  trade_date: date,
  open: '10',
  close: '12',
  low: '9',
  high: '13',
  factor,
  volume: '100',
  amount: '1000',
  source: 'BAOSTOCK',
  fetched_at: '',
  is_synthetic: false,
});
it('uses a common final-date adjustment anchor for candles and moving averages', () => {
  const prices = chartPrices(
    [bar('2026-11-11', '1'), bar('2026-11-12', '2')],
    [{ trade_date: '2026-11-11', ma20: '10' } as Indicator],
  );
  expect(prices.candles[0]).toEqual([5, 6, 4.5, 6.5]);
  expect(prices.ma20).toEqual([5, null]);
  expect(prices.warning).toBe('');
});
it('keeps gaps for missing factors, invalid OHLC and mismatched sources without creating zero prices', () => {
  const prices = chartPrices(
    [bar('a', null), { ...bar('b'), high: '8' }, { ...bar('c'), source: 'SINA' }, bar('d')],
    [],
  );
  expect(prices.candles).toEqual([
    ['-', '-', '-', '-'],
    ['-', '-', '-', '-'],
    ['-', '-', '-', '-'],
    [10, 12, 9, 13],
  ]);
  expect(prices.warning).toContain('3条');
  const unavailable = chartPrices([bar('a'), bar('b', null)], []);
  expect(unavailable.warning).toContain('暂不绘图');
  expect(unavailable.ma20).toEqual([null, null]);
});

it('does not show a false minus100 percent change for a missing latest factor or incomparable sources', () => {
  expect(comparableReturn(bar('b', null), bar('a'))).toBeNull();
  expect(comparableReturn({ ...bar('b'), source: 'SINA' }, bar('a'))).toBeNull();
  expect(comparableReturn({ ...bar('b'), close: '6', factor: '2' }, bar('a'))).toBe(0);
  expect(comparableReturn(bar('a'), bar('b'))).toBeNull();
});
