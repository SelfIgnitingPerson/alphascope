import { expect, it } from 'vitest';
import { readableJobError } from './job-errors';
it('summarizes the old stock error without inventing a missing date or showing channel codes', () => {
  const raw =
    '行情采集失败：002837.SZ（2024-11-12至2026-11-12，已保存485条/应有486个交易日；无完整可用版本）。所有真实行情通道不可用（不会回退模拟）：SINA:UNCLASSIFIED_FAILURE';
  const value = readableJobError(raw);
  expect(value.summary).toContain('缺1天');
  expect(value.summary).toContain('没有保存具体缺失日期');
  expect(value.summary).not.toContain('UNCLASSIFIED_FAILURE');
  expect(value.technical).toBe(raw);
});
it('does not claim a missing date when a forced refresh fails with a full local record count', () => {
  expect(
    readableJobError(
      '行情采集失败：002837.SZ（2024-11-12至2026-11-12，已保存486条/应有486个交易日；无完整可用版本）。X',
    ).summary,
  ).toContain('刷新或完整性核验没有通过');
});
