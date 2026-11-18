<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import ObservationSummary from '../common/ObservationSummary.vue';
import AsyncState from '../common/AsyncState.vue';
import { useResource } from '../../composables/useResource';
import { getResearchFocus } from '../../api/research-focus';
import type { PageProps } from '../../types/models';
const props = defineProps<PageProps>();
const page = ref(1);
const category = ref('ALL');
const expanded = ref(false);
watch([category, page, () => props.date], () => {
  expanded.value = false;
});
watch(
  () => props.date,
  () => {
    page.value = 1;
    category.value = 'ALL';
  },
);
const resource = useResource(
  (signal) => getResearchFocus({ asOf: props.date, page: page.value, size: 10 }, signal),
  {
    watch: [() => props.date, () => props.refreshToken, page],
    identity: () => `${props.date}:${page.value}`,
  },
);
const tabs = [
  { code: 'ALL', title: '全部事项' },
  { code: 'RISK', title: '优先核对' },
  { code: 'WATCH', title: '观察跟踪' },
  { code: 'MATCH', title: '条件满足' },
  { code: 'DATA_GAP', title: '资料待补' },
];
type FocusItem = NonNullable<Awaited<ReturnType<typeof getResearchFocus>>>['items'][number];
function matches(item: FocusItem, code: string) {
  const level = item.priorities?.[0]?.level;
  if (code === 'RISK') return item.category === 'RISK' && level !== 'WATCH';
  if (code === 'WATCH')
    return (
      item.category === 'WAIT' ||
      item.category === 'CHANGED' ||
      (item.category === 'RISK' && level === 'WATCH')
    );
  return code === 'ALL' || item.category === code;
}
const items = computed(() =>
  (resource.data.value?.items || []).filter((item) => matches(item, category.value)),
);
const visibleItems = computed(() => (expanded.value ? items.value : items.value.slice(0, 3)));
const count = (code: string) =>
  (resource.data.value?.items || []).filter((item) => matches(item, code)).length;
const leading = computed(() => resource.data.value?.items[0]);
</script>
<template>
  <SectionCard
    title="今天先看这几件事"
    description="先核对风险边界与已确认组合，再跟踪弱观察和候选条件。轻重来自现有证据，不是亏损概率。"
  >
    <template #actions>
      <RouterLink
        to="/plans"
        class="text-link"
      >
        跟踪已保存计划 →
      </RouterLink>
    </template>
    <AsyncState
      :loading="resource.loading.value"
      :error="resource.error.value"
      :has-content="Boolean(resource.data.value)"
      @retry="resource.reload"
    >
      <template v-if="resource.data.value">
        <p class="focus-scope">
          研究日 {{ resource.data.value.asOf }} · 本页核对 {{ resource.data.value.items.length }} /
          {{ resource.data.value.total }} 只启用自选。 按自选优先级分页，事项仅在本页按风险排序。
        </p>
        <p
          v-if="leading"
          class="focus-leading"
        >
          <strong>本页先看：{{ leading.name }} · {{ leading.title }}</strong>
          <span>{{ leading.changeLabel || '请先核对当前依据' }}</span>
        </p>
        <div
          class="focus-filters"
          role="group"
          aria-label="筛选本页研究重点"
        >
          <button
            v-for="tab in tabs"
            :key="tab.code"
            type="button"
            :aria-pressed="category === tab.code"
            @click="category = tab.code"
          >
            {{ tab.title }} {{ count(tab.code) }}
          </button>
        </div>
        <div
          v-if="items.length"
          class="focus-list"
        >
          <article
            v-for="item in visibleItems"
            :key="item.symbol"
            :data-category="item.category"
            class="focus-item"
          >
            <div class="focus-item__name">
              <strong>{{ item.name }}</strong>
              <small>{{ item.symbol }}</small>
            </div>
            <div class="focus-item__body">
              <ObservationSummary
                v-if="item.priorities?.length"
                :priority="item.priorities[0]"
                compact
              />
              <template v-else>
                <strong>{{ item.title }}</strong>
                <p>{{ item.explanation }}</p>
              </template>
              <small
                v-if="item.changeLabel"
                class="focus-change"
              >
                {{ item.changeLabel }}
              </small>
              <small>
                行情 {{ item.quoteDate || '暂无' }}
                <template v-if="item.comparisonDate">· 上期 {{ item.comparisonDate }}</template>
              </small>
            </div>
            <RouterLink
              :to="{
                path: `/stocks/${item.symbol}`,
                query: { date, ...(item.horizon ? { horizon: item.horizon } : {}) },
              }"
              class="text-link"
            >
              查看依据 →
            </RouterLink>
          </article>
        </div>
        <p
          v-else
          class="help-text"
          role="status"
        >
          {{
            resource.data.value.total
              ? '本页没有此类事项，可切换分类或查看下一页。'
              : '还没有启用的自选股，先添加关注股票，再查看每日研究重点。'
          }}
          <RouterLink
            v-if="!resource.data.value.total"
            to="/watchlist"
            class="text-link"
          >
            添加自选 →
          </RouterLink>
        </p>
        <button
          v-if="items.length > 3"
          type="button"
          class="focus-expand"
          @click="expanded = !expanded"
        >
          {{ expanded ? '收起其余事项' : `展开本页其余${items.length - 3}项（含待核对线索）` }}
        </button>
        <div
          v-if="resource.data.value.total > resource.data.value.size"
          class="focus-pagination"
        >
          <span>第 {{ page }} 页</span>
          <el-button
            :disabled="page <= 1 || resource.loading.value"
            @click="
              page--;
              category = 'ALL';
            "
          >
            上一页
          </el-button>
          <el-button
            :disabled="
              page * resource.data.value.size >= resource.data.value.total || resource.loading.value
            "
            @click="
              page++;
              category = 'ALL';
            "
          >
            下一页
          </el-button>
        </div>
        <details class="reading-details">
          <summary>这些事项怎样判断？</summary>
          <p>
            结构参考触及优先核对，其次是经营风险或已确认的组合；仅有弱走势、量能尚未确认时归入观察跟踪。只有同源、同方法且完整的上期对照，才能标记条件“新满足”。这里比较的是当前已保存版本，不证明历史时点已可得，也不代表策略已验证有效。其他周期仍需进入个股查看。
          </p>
        </details>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.focus-scope {
  font-size: 12px;
  color: #738398;
  line-height: 1.8;
}
.focus-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}
.focus-filters button {
  border: 1px solid #e2e9ef;
  border-radius: 6px;
  background: #fff;
  color: #536a80;
  padding: 8px 12px;
  cursor: pointer;
}
.focus-filters button[aria-pressed='true'] {
  color: #235970;
  background: #eaf2f4;
  border-color: #235970;
}
.focus-item {
  display: grid;
  grid-template-columns: minmax(90px, 0.45fr) minmax(0, 3fr) auto;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #eef2f6;
  align-items: start;
}
.focus-item__name small {
  display: block;
  margin-top: 6px;
}
.focus-item small {
  color: #738398;
  font-size: 12px;
}
.focus-item strong {
  font-size: 14px;
}
.focus-item p {
  font-size: 13px;
  line-height: 1.8;
  margin: 7px 0;
}
.focus-item[data-category='RISK'] .focus-item__body > strong {
  color: #b14747;
}
.focus-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 16px;
}
.focus-leading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f4f7fa;
  font-size: 13px;
}
.focus-leading span,
.focus-change {
  color: #738398;
  font-size: 12px;
}
.focus-change {
  display: block;
  margin: 8px 0 4px;
}
.focus-expand {
  margin-top: 12px;
  padding: 9px 14px;
  background: #fff;
  border: 1px solid #dce5ed;
  border-radius: 6px;
  color: #235970;
  cursor: pointer;
}
@media (max-width: 700px) {
  .focus-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
}
</style>
