import { ref, shallowRef, watch, onScopeDispose, type WatchSource } from 'vue';
/** 同一查询刷新可保留已知内容；切换证券、日期或筛选身份必须清空旧结果。 */
export interface ResourceOptions {
  watch?: WatchSource<unknown>[];
  identity?: () => string;
}
/** 新请求和页面销毁取消旧请求；序号与查询身份共同防止旧响应回写。 */
export function useResource<T>(
  loader: (signal: AbortSignal) => Promise<T>,
  sourcesOrOptions: WatchSource<unknown>[] | ResourceOptions = [],
) {
  const options = Array.isArray(sourcesOrOptions) ? { watch: sourcesOrOptions } : sourcesOrOptions;
  const sources = options.watch || [];
  let identity: string | undefined;
  const data = shallowRef<T>(),
    loading = ref(false),
    error = ref('');
  let sequence = 0;
  let controller: AbortController | undefined;
  async function reload() {
    controller?.abort();
    const current = ++sequence;
    const nextIdentity = options.identity?.() || 'default';
    if (identity !== undefined && identity !== nextIdentity) data.value = undefined;
    identity = nextIdentity;
    const request = new AbortController();
    controller = request;
    loading.value = true;
    error.value = '';
    try {
      const result = await loader(request.signal);
      if (current === sequence && !request.signal.aborted) {
        data.value = result;
      }
    } catch (reason) {
      if (current === sequence && !request.signal.aborted) {
        error.value = reason instanceof Error ? reason.message : String(reason);
      }
    } finally {
      if (current === sequence) {
        loading.value = false;
      }
    }
  }
  if (sources.length) {
    watch(sources, reload, { immediate: true });
  } else {
    void reload();
  }
  onScopeDispose(() => {
    sequence++;
    controller?.abort();
  });
  return { data, loading, error, reload };
}
