import { it, expect } from 'vitest';
import { effectScope, ref, nextTick } from 'vue';
import { useResource } from './useResource';
it('cancels replaced and disposed requests and ignores stale results', async () => {
  const date = ref('a');
  const requests: { signal: AbortSignal; resolve: (value: string) => void }[] = [];
  const scope = effectScope();
  const resource = scope.run(() =>
    useResource(
      (signal) => new Promise<string>((resolve) => requests.push({ signal, resolve })),
      [date],
    ),
  )!;
  date.value = 'b';
  await nextTick();
  expect(requests[0].signal.aborted).toBe(true);
  requests[1].resolve('new');
  await Promise.resolve();
  requests[0].resolve('old');
  await Promise.resolve();
  expect(resource.data.value).toBe('new');
  scope.stop();
  expect(requests[1].signal.aborted).toBe(true);
});
it('clears previous query results on identity changes but preserves same-query refresh content', async () => {
  const date = ref('a');
  let fail = false;
  const scope = effectScope();
  const resource = scope.run(() =>
    useResource(
      async () => {
        if (fail) throw new Error('unavailable');
        return date.value;
      },
      { watch: [date], identity: () => date.value },
    ),
  )!;
  await Promise.resolve();
  expect(resource.data.value).toBe('a');
  fail = true;
  await resource.reload();
  expect(resource.data.value).toBe('a');
  date.value = 'b';
  await nextTick();
  await Promise.resolve();
  expect(resource.data.value).toBeUndefined();
  expect(resource.error.value).toBe('unavailable');
  scope.stop();
});
