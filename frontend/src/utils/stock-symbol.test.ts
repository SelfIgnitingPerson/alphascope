import { expect, it } from 'vitest';
import { normalizeStockSymbol } from './stock-symbol';

it.each([
  ['002837', '002837.SZ'],
  ['300476', '300476.SZ'],
  ['600000', '600000.SH'],
  ['688001', '688001.SH'],
  ['830799', '830799.BJ'],
  ['920001', '920001.BJ'],
  [' 002837.sz ', '002837.SZ'],
  ['abc', null],
  ['00283', null],
  ['123456', null],
  ['002837.US', null],
])('normalizes supported stock codes without guessing unknown markets: %s', (input, expected) => {
  expect(normalizeStockSymbol(input)).toBe(expected);
});
