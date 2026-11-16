import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { api } from './client';
import { getBars, getBacktest } from './market';
import { getReports } from './reports';
import { getJobs, runJob } from './jobs';
import { savePosition } from './portfolio';

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset().mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          code: 'SUCCESS',
          message: '成功',
          data: null,
          requestId: 'request-contract',
        }),
      ),
  );
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

it('keeps report query names and defaults after accepting an object', async () => {
  await getReports({ type: 'DAILY', end: '2026-10-30', size: 5 });
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
  expect(url.pathname).toBe('/api/v1/reports');
  const parameters: Record<string, string> = {};
  url.searchParams.forEach((value, key) => {
    parameters[key] = value;
  });
  expect(parameters).toEqual({
    type: 'DAILY',
    end: '2026-10-30',
    start: '1990-01-01',
    page: '1',
    size: '5',
    latestOnly: 'true',
  });
});
it('keeps explicit false and named pagination conditions', async () => {
  await getReports({ type: 'WEEKLY', end: '2026-10-30', page: 2, latestOnly: false });
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
  expect(url.searchParams.get('latestOnly')).toBe('false');
  expect(url.searchParams.get('page')).toBe('2');
  await getJobs({ page: 3, size: 50 });
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/jobs?page=3&size=50');
});
it('keeps resource identity separate from the date query', async () => {
  await getBars('300476.SZ', { start: '2026-10-01', end: '2026-10-30' });
  expect(fetchMock.mock.calls[0][0]).toBe(
    '/api/v1/instruments/300476.SZ/bars?start=2026-10-01&end=2026-10-30',
  );
  await getBacktest({ symbol: '300476.SZ', start: '2026-10-01', end: '2026-10-30' });
  const url = new URL(fetchMock.mock.calls[1][0], 'http://localhost');
  expect(url.searchParams.get('symbol')).toBe('300476.SZ');
});
it('keeps asynchronous task submission in POST query parameters', async () => {
  await runJob({ type: 'WEEKLY', date: '2026-10-30', force: true });
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/jobs/weekly?date=2026-10-30&force=true');
  expect(fetchMock.mock.calls[0][1].method).toBe('POST');
});
it('keeps the published position JSON property names and units', async () => {
  const input = { quantity: 100, costPrice: 12.5, asOfDate: '2026-10-30' };
  await savePosition('300476.SZ', input);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/portfolio/positions/300476.SZ');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(input);
});
it.each([
  new Headers({ 'X-Test': 'headers', 'Content-Type': 'application/custom' }),
  [
    ['X-Test', 'pairs'],
    ['Content-Type', 'application/custom'],
  ] as [string, string][],
])('preserves all RequestInit header forms', async (headers) => {
  await api('/headers', { headers });
  const sent = new Headers(fetchMock.mock.calls[0][1].headers);
  expect(sent.get('X-Test')).toBeTruthy();
  expect(sent.get('Content-Type')).toBe('application/custom');
});
