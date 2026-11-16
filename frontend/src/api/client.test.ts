import { describe, it, expect, vi, afterEach } from 'vitest';
import { api, ApiError } from './client';
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
describe('HTTP lifecycle', () => {
  it('keeps the server request id visible', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'BROKEN', message: '失败', data: null, requestId: 'req-123' }),
          {
            status: 500,
          },
        ),
      ),
    );
    await expect(api('/x')).rejects.toThrow('req-123');
  });
  it('accepts an asynchronous 202 job ticket', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'SUCCESS',
            message: '已受理',
            data: { id: 12, status: 'PENDING' },
            requestId: 'req-202',
          }),
          { status: 202 },
        ),
      ),
    );
    expect(await api('/jobs')).toEqual({ id: 12, status: 'PENDING' });
  });
  it('rejects a business failure even when HTTP succeeded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'DATA_UNAVAILABLE',
            message: '缺少真实行情',
            data: null,
            requestId: 'req-business',
          }),
          { status: 200 },
        ),
      ),
    );
    await expect(api('/x')).rejects.toMatchObject({
      code: 'DATA_UNAVAILABLE',
      requestId: 'req-business',
    });
  });
  it('rejects an unwrapped or malformed response instead of treating it as business data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ id: 12 }), {
          status: 200,
          headers: { 'X-Request-Id': 'req-invalid' },
        }),
      ),
    );
    await expect(api('/x')).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
      requestId: 'req-invalid',
    });
  });
  it('allows an empty successful write result', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'SUCCESS',
            message: '操作成功',
            data: null,
            requestId: 'req-write',
          }),
        ),
      ),
    );
    expect(await api('/x')).toBeNull();
  });
  it('aborts a timed out request', async () => {
    vi.useFakeTimers();
    let signal: AbortSignal;
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, options) =>
          new Promise((_resolve, reject) => {
            signal = options.signal;
            signal.addEventListener('abort', () =>
              reject(new DOMException('aborted', 'AbortError')),
            );
          }),
      ),
    );
    const result = api('/slow', { timeoutMs: 10 }).catch((e) => e);
    await vi.advanceTimersByTimeAsync(11);
    expect(signal!.aborted).toBe(true);
    expect(((await result) as ApiError).code).toBe('TIMEOUT');
  });
});

it('explains fetch failure without echoing raw URLs or claiming the submission failed', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('secret-url?token=hidden')));
  const error = (await api('/jobs', { method: 'POST' }).catch(
    (error: ApiError) => error,
  )) as ApiError;
  expect(error.code).toBe('NETWORK_ERROR');
  expect(error.message).toContain('先查询是否已受理');
  expect(error.message).not.toContain('hidden');
});
