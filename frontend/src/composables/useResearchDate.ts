import { computed, nextTick, onScopeDispose, ref, watch } from 'vue';
import { useRoute, useRouter, type RouteLocationNormalized } from 'vue-router';
import { getWorkspaceStatus, type WorkspaceStatus } from '../api/product';
import { today } from '../utils/date';

type DateBasis = 'calendar' | 'link' | 'manual' | 'unverified';
/** 校验完整ISO日期及上海当前日期，不把非法日期交给各业务接口。 */
export function validResearchDate(value: unknown): value is string {
  if (
    typeof value !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
    value < '1990-01-01' ||
    value > today()
  )
    return false;
  const parsed = new Date(value + 'T12:00:00Z');
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}
/** 默认取权威日历确认的收盘日；明确选择与链接日期不会被迟到响应或刷新覆盖。 */
export function useResearchDate(options: {
  session: () => string;
  load?: (signal: AbortSignal) => Promise<WorkspaceStatus>;
}) {
  const route = useRoute(),
    router = useRouter();
  const date = ref(today()),
    ready = ref(false),
    loading = ref(false),
    warning = ref('');
  const basis = ref<DateBasis>('unverified');
  let generation = 0;
  let controller: AbortController | undefined;
  const label = computed(
    () =>
      ({
        calendar: '按已确认收盘交易日初始化',
        link: '链接指定日期',
        manual: '手动选择日期',
        unverified: '收盘交易日尚未确认',
      })[basis.value],
  );
  function accept(value: string, source: DateBasis) {
    generation++;
    controller?.abort();
    loading.value = false;
    date.value = value;
    basis.value = source;
    ready.value = true;
  }
  async function latest() {
    if (!options.session()) return;
    controller?.abort();
    const request = new AbortController();
    controller = request;
    const current = ++generation;
    loading.value = true;
    try {
      const status = await (options.load || getWorkspaceStatus)(request.signal);
      if (request.signal.aborted || current !== generation) return;
      if (status.calendarStatus === 'AVAILABLE' && validResearchDate(status.expectedTradeDate)) {
        date.value = status.expectedTradeDate;
        basis.value = 'calendar';
        warning.value = '';
      } else {
        warning.value =
          '近期权威交易日历不足，不能确认最近收盘日。请核对并手动选择研究日期；当前日期不代表已取得行情。';
      }
    } catch {
      if (request.signal.aborted || current !== generation) return;
      warning.value =
        '最近收盘日核查暂不可用，已保留当前日期。请手动核对；不会按星期或旧行情猜测最新交易日。';
    } finally {
      if (current === generation && !request.signal.aborted) {
        ready.value = true;
        loading.value = false;
      }
    }
  }
  function select(value: string) {
    if (!validResearchDate(value)) {
      warning.value = '请选择1990年以来、不晚于上海今天的有效日期。';
      return;
    }
    accept(value, 'manual');
    warning.value = '';
  }
  watch(
    options.session,
    (session) => {
      generation++;
      controller?.abort();
      loading.value = false;
      ready.value = false;
      if (!session) return;
      date.value = today();
      basis.value = 'unverified';
      warning.value = '';
      const explicit = route.meta.hideBusinessDate !== true ? route.query.date : undefined;
      if (validResearchDate(explicit)) accept(explicit, 'link');
      else {
        if (explicit !== undefined) warning.value = '链接中的研究日期无效，正在核对可用的收盘日。';
        void latest();
      }
    },
    { immediate: true },
  );
  watch(
    () => route.query.date,
    (value) => {
      if (!options.session() || route.meta.hideBusinessDate === true || value === undefined) return;
      if (!validResearchDate(value)) {
        warning.value = '链接中的研究日期无效，保留当前日期；请选择有效业务日。';
        return;
      }
      if (!ready.value || value !== date.value) {
        accept(value, 'link');
        warning.value = '';
      }
    },
  );
  // 日期响应可能与懒加载路由并行：不可用旧页面的URL替换取消用户正在进行的导航。
  let pendingRoute: RouteLocationNormalized | undefined;
  const removeBefore = router.beforeEach((to) => {
    pendingRoute = to;
  });
  const removeAfter = router.afterEach((to, _from, failure) => {
    if (pendingRoute === to) pendingRoute = undefined;
    if (!failure) void nextTick(syncUrl);
  });
  function syncUrl() {
    if (
      !ready.value ||
      !options.session() ||
      pendingRoute ||
      !route.matched.length ||
      route.meta.hideBusinessDate === true ||
      route.query.date === date.value ||
      router.options.history.location !== route.fullPath
    )
      return;
    // 查询身份写入URL，使刷新、新标签和前进后退保持同一研究日期。
    void router.replace({
      path: route.path,
      query: { ...route.query, date: date.value },
      hash: route.hash,
    });
  }
  watch([() => route.fullPath, ready, date], syncUrl, { flush: 'post' });
  onScopeDispose(() => {
    generation++;
    controller?.abort();
    removeBefore();
    removeAfter();
  });
  return { date, ready, loading, warning, label, select, latest };
}
