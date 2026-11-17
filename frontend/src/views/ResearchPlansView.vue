<script setup lang="ts">
import { ref } from 'vue';
import SectionCard from '../components/common/SectionCard.vue';
import AsyncState from '../components/common/AsyncState.vue';
import { useResource } from '../composables/useResource';
import { useMutation } from '../composables/useMutation';
import {
  getPlans,
  getPlan,
  getPlanReviews,
  reviewPlan,
  archivePlan,
  type ResearchPlan,
  type PlanReview,
  type PlanFilters,
} from '../api/plans';
import { number, timestamp } from '../utils/format';
import type { PageProps, PageResponse } from '../types/models';
const props = defineProps<PageProps>();
const page = ref(1);
const filters = ref<Omit<PlanFilters, 'page' | 'size'>>({});
const symbol = ref('');
const horizon = ref('');
const archiveStatus = ref('');
const plan = ref<ResearchPlan>();
const { data, loading, error, reload } = useResource(
  (signal) => getPlans({ ...filters.value, page: page.value, size: 20 }, signal),
  {
    watch: [() => page.value, () => filters.value, () => props.refreshToken],
    identity: () => JSON.stringify({ ...filters.value, page: page.value }),
  },
);
const mutation = useMutation();
const reviews = ref<PageResponse<PlanReview>>();
function applyFilters() {
  page.value = 1;
  filters.value = {
    symbol: symbol.value.trim() || undefined,
    horizon: horizon.value || undefined,
    archived: archiveStatus.value === '' ? undefined : archiveStatus.value === 'archived',
  };
  plan.value = undefined;
  reviews.value = undefined;
}
async function select(id: number) {
  await mutation.run(async () => {
    plan.value = await getPlan(id);
    reviews.value = undefined;
  });
}
async function read(id: number, reviewPage = 1) {
  await mutation.run(async () => {
    reviews.value = await getPlanReviews(id, reviewPage);
  });
}
async function review(id: number) {
  await mutation.run(async () => {
    await reviewPlan(id, props.date);
    reviews.value = await getPlanReviews(id);
  });
}
async function archive(id: number) {
  await mutation.run(async () => {
    await archivePlan(id);
    plan.value = await getPlan(id);
    await reload();
  });
}
const horizonLabels: Record<string, string> = { SHORT: '短期', MEDIUM: '中期', LONG: '长期' };
const statuses: Record<string, string> = {
  UNCHANGED: '行情状态未变化',
  CONDITIONS_CHANGED: '行情条件已变化',
  ORIGINAL_STOP_BREACHED: '原风险线已触及',
  METHOD_CHANGED: '方法已变更，需重评',
  SOURCE_CHANGED: '来源已变化，需核对',
  DATA_INSUFFICIENT: '复核资料不足',
  REVIEW_REQUIRED: '需人工复核',
};
</script>
<template>
  <div class="page-stack">
    <div class="notice notice--subtle">
      在个股研究中保存计划；这里保留原价位、依据与方法。选择研究日后显式复核，刷新只读取。没有自动下单或个人仓位建议。
    </div>
    <el-alert
      v-if="mutation.error.value"
      :title="mutation.error.value"
      type="error"
      :closable="false"
    />
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <SectionCard
        title="个人研究计划"
        description="先按证券、周期和归档状态筛选，再打开原始证据。归档保留记录，不释放存储配额。"
      >
        <form
          class="plan-filters"
          @submit.prevent="applyFilters"
        >
          <el-input
            v-model="symbol"
            placeholder="完整代码，如002837.SZ"
            aria-label="证券代码"
            clearable
          />
          <el-select
            v-model="horizon"
            placeholder="全部周期"
            clearable
            aria-label="研究周期"
          >
            <el-option
              label="短期"
              value="SHORT"
            />
            <el-option
              label="中期"
              value="MEDIUM"
            />
            <el-option
              label="长期"
              value="LONG"
            />
          </el-select>
          <el-select
            v-model="archiveStatus"
            placeholder="全部状态"
            clearable
            aria-label="归档状态"
          >
            <el-option
              label="跟踪中"
              value="active"
            />
            <el-option
              label="已归档"
              value="archived"
            />
          </el-select>
          <el-button
            native-type="submit"
            type="primary"
          >
            查询计划
          </el-button>
        </form>
        <el-table
          :data="data?.items || []"
          empty-text="暂无匹配计划，请在个股研究中保存计划。"
        >
          <el-table-column
            label="证券"
            min-width="160"
          >
            <template #default="{ row }">{{ row.name }} {{ row.symbol }}</template>
          </el-table-column>
          <el-table-column
            label="周期"
            width="80"
          >
            <template #default="{ row }">{{ horizonLabels[row.horizon] }}</template>
          </el-table-column>
          <el-table-column
            prop="asOf"
            label="原研究日"
            width="120"
          />
          <el-table-column
            label="状态"
            width="100"
          >
            <template #default="{ row }">{{ row.archived ? '已归档' : '跟踪中' }}</template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="100"
          >
            <template #default="{ row }">
              <el-button
                :disabled="mutation.busy.value"
                @click="select(row.id)"
              >
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div
          class="plan-operations"
          v-if="data"
        >
          <span>共 {{ data.total }} 份 · 第 {{ page }} 页</span>
          <el-button
            :disabled="page <= 1 || loading"
            @click="page--"
          >
            上一页
          </el-button>
          <el-button
            :disabled="page * 20 >= data.total || loading"
            @click="page++"
          >
            下一页
          </el-button>
        </div>
      </SectionCard>
      <SectionCard
        v-if="plan"
        :title="`${plan.research.name} · ${plan.research.symbol}${plan.archived ? ' · 已归档' : ''}`"
        :description="`原研究日 ${plan.research.asOf} · 保存于 ${timestamp(plan.createdAt)}`"
      >
        <p>
          {{ plan.research.horizons.find((h) => h.code === plan?.horizon)?.title }} ·
          {{
            plan.research.horizons
              .find((h) => h.code === plan?.horizon)
              ?.actions.find((a) => a.code === plan?.actionCode)?.title
          }}
        </p>
        <p>
          原研究收盘：
          <strong class="reference-price">{{ number(plan.research.close) }} 元</strong>
          · 方法 {{ plan.research.version }}
        </p>
        <div class="plan-operations">
          <el-button
            v-if="!plan.archived"
            :loading="mutation.busy.value"
            @click="review(plan.id)"
          >
            按 {{ date }} 追加复核
          </el-button>
          <el-button
            :disabled="mutation.busy.value"
            @click="read(plan.id)"
          >
            查看已存复核
          </el-button>
          <el-button
            v-if="!plan.archived"
            :disabled="mutation.busy.value"
            @click="archive(plan.id)"
          >
            归档并保留证据
          </el-button>
        </div>
        <details class="reading-details">
          <summary>查看原始条件、参考价与失效前提</summary>
          <template
            v-for="h in plan.research.horizons.filter((h) => h.code === plan?.horizon)"
            :key="h.code"
          >
            <article
              v-for="a in h.actions.filter((a) => a.code === plan?.actionCode)"
              :key="a.code"
            >
              <p>{{ a.conclusion }}</p>
              <p v-if="a.stopReference != null">
                原结构风险线：
                <strong class="reference-price">{{ number(a.stopReference) }} 元</strong>
              </p>
              <ul>
                <li
                  v-for="c in a.conditions"
                  :key="c.label"
                >
                  {{ c.label }}：{{ c.observed }}；要求 {{ c.required }}
                </li>
                <li
                  v-for="v in a.invalidation"
                  :key="v"
                >
                  {{ v }}
                </li>
              </ul>
            </article>
          </template>
          <p class="help-text">证据指纹：{{ plan.snapshotHash }}</p>
        </details>
        <div v-if="reviews">
          <p
            v-if="!reviews.items.length"
            class="help-text"
          >
            尚无已保存复核。
          </p>
          <article
            v-for="(item, index) in reviews.items"
            :key="index"
            class="plan-review"
          >
            <strong>{{ item.date }} · {{ statuses[item.status] || '待核对' }}</strong>
            <p>{{ item.explanation }}</p>
            <p v-if="item.adjustedOriginalStop != null">
              原风险线按同源因子换算：
              <strong class="reference-price">{{ number(item.adjustedOriginalStop) }} 元</strong>
              ；当前收盘 {{ number(item.close) }} 元
            </p>
            <p
              v-for="v in item.limitations"
              :key="v"
              class="help-text"
            >
              {{ v }}
            </p>
          </article>
          <div class="plan-operations">
            <span>共 {{ reviews.total }} 条复核 · 第 {{ reviews.page }} 页</span>
            <el-button
              :disabled="reviews.page <= 1 || mutation.busy.value"
              @click="read(plan.id, reviews.page - 1)"
            >
              上一页复核
            </el-button>
            <el-button
              :disabled="reviews.page * reviews.size >= reviews.total || mutation.busy.value"
              @click="read(plan.id, reviews.page + 1)"
            >
              下一页复核
            </el-button>
          </div>
        </div>
      </SectionCard>
    </AsyncState>
  </div>
</template>
<style scoped>
.plan-filters {
  display: grid;
  grid-template-columns: minmax(160px, 2fr) repeat(2, minmax(110px, 1fr)) auto;
  gap: 12px;
  margin-bottom: 20px;
}
@media (max-width: 700px) {
  .plan-filters {
    grid-template-columns: 1fr;
  }
}

.plan-operations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}
.plan-operations .el-button {
  margin-left: 0;
}
.plan-review {
  padding: 16px;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  margin-top: 12px;
}
.help-text {
  overflow-wrap: anywhere;
}
</style>
