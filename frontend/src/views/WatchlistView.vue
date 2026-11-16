<script setup lang="ts">
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { ref, watch, onScopeDispose } from 'vue';
import {
  getWatchlist,
  removeWatch,
  discoverInstruments,
  addDiscoveredWatch,
  type InstrumentCandidate,
} from '../api/portfolio';
import { useMutation } from '../composables/useMutation';
const props = defineProps<PageProps>();
const symbol = ref('');
const keyword = ref('');
const candidates = ref<InstrumentCandidate[]>([]);
const searchMessage = ref('');
const resultMessage = ref('');
const jobId = ref<number | null>(null);
const initialize = ref(true);
let searchController: AbortController | undefined;
let searchSequence = 0;
// 候选只属于本次关键词；输入变化与失败后不能继续添加上一查询的股票。
watch(
  keyword,
  () => {
    searchSequence++;
    searchController?.abort();
    candidates.value = [];
    symbol.value = '';
    searchMessage.value = '';
    mutationError.value = '';
  },
  { flush: 'sync' },
);
onScopeDispose(() => searchController?.abort());
async function search() {
  const query = keyword.value.trim();
  if (!query) {
    mutationError.value = '请输入股票代码或名称后搜索。';
    return;
  }
  const current = ++searchSequence;
  searchController?.abort();
  const controller = new AbortController();
  searchController = controller;
  candidates.value = [];
  symbol.value = '';
  searchMessage.value = '';
  await mutation.run(async () => {
    try {
      const found = await discoverInstruments(query, controller.signal);
      if (current !== searchSequence || controller.signal.aborted) return;
      candidates.value = found.items;
      searchMessage.value = found.message;
    } catch (reason) {
      if (current === searchSequence && !controller.signal.aborted) throw reason;
    }
  });
}
const { data, loading, error, reload } = useResource(getWatchlist, [() => props.refreshToken]);
const mutation = useMutation();
const { busy, error: mutationError } = mutation;
async function add() {
  if (
    !/^\d{6}\.(SH|SZ|BJ)$/.test(symbol.value.trim()) ||
    !candidates.value.some((item) => item.symbol === symbol.value.trim())
  ) {
    mutationError.value = '请先搜索并选择本次已核验的股票。';
    return;
  }
  await mutation.run(async () => {
    const date = props.date;
    const result = await addDiscoveredWatch(symbol.value.trim(), date, initialize.value);
    resultMessage.value = `研究日期 ${date} · ${result.message}`;
    jobId.value = result.jobId;
    symbol.value = '';
    await reload();
  });
}
async function remove(value: string) {
  await mutation.run(async () => {
    await removeWatch(value);
    await reload();
  });
}
</script>
<template>
  <SectionCard
    title="我的关注列表"
    description="搜索真实A股，选择后加入自己的自选；首次初始化进度可在任务中心查看。"
  >
    <template #actions>
      <span class="count-label">{{ data?.length ?? '—' }} 只关注</span>
    </template>
    <form
      class="filter-bar filter-bar--search"
      @submit.prevent="search"
    >
      <el-input
        v-model="keyword"
        placeholder="输入代码或名称，例如 600519 或 贵州茅台"
        aria-label="搜索股票"
        maxlength="40"
        clearable
      />
      <el-button
        data-testid="watch-search"
        native-type="submit"
        :loading="busy"
      >
        搜索股票
      </el-button>
    </form>
    <p
      v-if="searchMessage"
      class="help-text"
      role="status"
    >
      {{ searchMessage }}
    </p>
    <form
      class="filter-bar filter-bar--watch-add"
      @submit.prevent="add"
    >
      <label class="filter-field">
        选择股票
        <el-select
          v-model="symbol"
          placeholder="选择已核验股票"
          aria-label="股票候选"
          filterable
        >
          <el-option
            v-for="item in candidates"
            :key="item.symbol"
            :value="item.symbol"
            :label="`${item.name} · ${item.symbol}${item.registered ? '（已登记）' : ''}`"
          />
        </el-select>
      </label>
      <el-checkbox v-model="initialize">添加后初始化 {{ date }} 的研究</el-checkbox>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
        data-testid="watch-add"
        :disabled="!symbol"
      >
        添加关注
      </el-button>
    </form>
    <p
      v-if="resultMessage"
      class="help-text"
      role="status"
    >
      {{ resultMessage }}
      <RouterLink
        v-if="jobId"
        to="/jobs"
        class="text-link"
      >
        查看任务 #{{ jobId }} →
      </RouterLink>
    </p>
    <el-alert
      v-if="mutationError"
      :title="mutationError"
      type="error"
      :closable="false"
      class="form-error"
    />
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <el-table
        :data="data || []"
        empty-text="还没有关注标的"
      >
        <el-table-column
          prop="symbol"
          label="股票代码"
          min-width="130"
        />
        <el-table-column
          prop="name"
          label="名称"
          min-width="120"
        />
        <el-table-column
          label="操作"
          min-width="190"
          align="right"
        >
          <template #default="s">
            <RouterLink
              :to="`/stocks/${s.row.symbol}`"
              class="text-link"
            >
              研究与买卖计划
            </RouterLink>
            <el-popconfirm
              title="确认移除这只自选股？"
              confirm-button-text="移除"
              cancel-button-text="取消"
              @confirm="remove(s.row.symbol)"
            >
              <template #reference>
                <el-button
                  link
                  type="danger"
                  :disabled="busy"
                  class="table-action"
                >
                  移除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </AsyncState>
  </SectionCard>
</template>
