<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import ForwardValidationPanel from '../components/research/ForwardValidationPanel.vue';
import {
  planActionLabels,
  planReviewLabels as statuses,
  planReviewExplanations,
  researchMethodLabel,
} from '../content/plan-labels';
import PlanTodoPanel from '../components/research/PlanTodoPanel.vue';
import ObservationSummary from '../components/common/ObservationSummary.vue';
import { planObservation } from '../utils/observation-summaries';
import TermHelp from '../components/common/TermHelp.vue';
import RiskReferenceGuide from '../components/research/RiskReferenceGuide.vue';
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
const props = defineProps<PageProps & { initialPlanId?: number }>();
const page = ref(1);
const filters = ref<Omit<PlanFilters, 'page' | 'size'>>({ archived: false });
const symbol = ref('');
const horizon = ref('');
const archiveStatus = ref('active');
const plan = ref<ResearchPlan>();
const { data, loading, error, reload } = useResource(
  (signal) => getPlans({ ...filters.value, asOf: props.date, page: page.value, size: 20 }, signal),
  {
    watch: [() => page.value, () => filters.value, () => props.date, () => props.refreshToken],
    identity: () => JSON.stringify({ ...filters.value, asOf: props.date, page: page.value }),
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
    reviews.value = await getPlanReviews(id);
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
    plan.value = await getPlan(id);
    reviews.value = await getPlanReviews(id);
    await reload();
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
const pageFocus = ref('ALL');
const rowFocus = (row: import('../api/plans').PlanSummary) =>
  row.archived || row.asOf > props.date
    ? 'ARCHIVED'
    : row.latestReviewDate &&
        row.latestReviewDate <= props.date &&
        row.latestReviewStatus === 'ORIGINAL_STOP_BREACHED'
      ? 'RISK'
      : row.latestReviewDate &&
          row.latestReviewDate <= props.date &&
          ['CONDITIONS_CHANGED', 'METHOD_CHANGED', 'SOURCE_CHANGED'].includes(
            row.latestReviewStatus || '',
          )
        ? 'CHANGED'
        : row.latestReviewDate &&
            row.latestReviewDate <= props.date &&
            row.latestReviewStatus === 'DATA_INSUFFICIENT'
          ? 'DATA'
          : row.latestReviewDate !== props.date
            ? 'DUE'
            : 'REVIEWED';
const focusTabs = [
  { code: 'ALL', label: '全部计划' },
  { code: 'RISK', label: '原风险线已触及' },
  { code: 'CHANGED', label: '条件或口径变化' },
  { code: 'DATA', label: '复核资料不足' },
  { code: 'DUE', label: '尚待所选日复核' },
];
const visiblePlans = computed(() =>
  (data.value?.items || [])
    .filter((row) => pageFocus.value === 'ALL' || rowFocus(row) === pageFocus.value)
    .sort(
      (a, b) =>
        ['RISK', 'CHANGED', 'DATA', 'DUE', 'REVIEWED', 'ARCHIVED'].indexOf(rowFocus(a)) -
        ['RISK', 'CHANGED', 'DATA', 'DUE', 'REVIEWED', 'ARCHIVED'].indexOf(rowFocus(b)),
    ),
);
watch([() => props.date, page, filters], () => {
  pageFocus.value = 'ALL';
});
const dueOnPage = computed(
  () =>
    (data.value?.items || []).filter(
      (row) => !row.archived && row.asOf <= props.date && row.latestReviewDate !== props.date,
    ).length,
);
watch(
  () => props.initialPlanId,
  (id) => {
    if (id && Number.isSafeInteger(id) && id > 0) void select(id);
  },
  { immediate: true },
);
</script>
<template>
  <div class="page-stack">
    <PlanTodoPanel
      :date="props.date"
      :refresh-token="props.refreshToken"
      @changed="reload"
    />
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
        <ObservationSummary
          v-if="data?.items.length"
          :priority="planObservation({ rows: data.items, date })"
          context="研究计划先处理什么"
        />
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
        <p
          class="help-text"
          v-if="data?.items.length"
          data-testid="plan-due-count"
        >
          本页 {{ dueOnPage }} 份跟踪计划尚无
          {{ date }} 的最近复核。日期不同的旧复核不代表本日条件仍成立。
        </p>
        <div
          class="plan-focus-filters"
          role="group"
          aria-label="筛选本页计划重点"
        >
          <button
            v-for="tab in focusTabs"
            :key="tab.code"
            type="button"
            :aria-pressed="pageFocus === tab.code"
            @click="pageFocus = tab.code"
          >
            {{ tab.label }}
            {{
              (data?.items || []).filter((row) => tab.code === 'ALL' || rowFocus(row) === tab.code)
                .length
            }}
          </button>
        </div>
        <p class="help-text">上述分类与排序只针对本页；分页之外的计划尚未在本页列出。</p>
        <el-table
          :data="visiblePlans"
          empty-text="暂无匹配计划，请在个股研究中保存计划。"
        >
          <el-table-column
            label="证券"
            min-width="160"
          >
            <template #default="{ row }">{{ row.name }} {{ row.symbol }}</template>
          </el-table-column>
          <el-table-column
            label="观察方案"
            min-width="180"
          >
            <template #default="{ row }">
              {{ planActionLabels[row.actionCode] || '历史方案（见原始记录）' }}
            </template>
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
            label="最近保存复核"
            min-width="220"
          >
            <template #default="{ row }">
              <template v-if="row.latestReviewDate">
                <strong>
                  {{ row.latestReviewDate }} · {{ statuses[row.latestReviewStatus] || '待核对' }}
                </strong>
                <p class="help-text">
                  {{
                    row.latestReviewStatus === 'UNCHANGED'
                      ? planReviewExplanations.UNCHANGED
                      : row.latestReviewExplanation
                  }}
                </p>
              </template>
              <span v-else>尚未复核</span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            min-width="190"
          >
            <template #default="{ row }">
              <el-button
                :disabled="mutation.busy.value"
                @click="select(row.id)"
              >
                查看详情
              </el-button>
              <el-button
                v-if="!row.archived && row.asOf <= date"
                :disabled="mutation.busy.value"
                @click="review(row.id)"
              >
                按 {{ date }} 复核
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
        </p>
        <div class="plan-operations">
          <RouterLink
            :to="{
              path: `/stocks/${plan.research.symbol}`,
              query: { date, horizon: plan.horizon },
            }"
            class="text-link"
          >
            查看当前个股依据 →
          </RouterLink>
          <el-button
            v-if="!plan.archived && plan.research.asOf <= date"
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
        <ForwardValidationPanel
          :date="date"
          :refresh-token="refreshToken"
          :symbol="plan.research.symbol"
          :plan-id="plan.id"
        />
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
                <TermHelp
                  term="original-risk-reference"
                  label="原方案风险参考"
                />
                ：
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
        </details>
        <details
          class="reading-details"
          data-testid="plan-method-details"
        >
          <summary>查看分析规则与追溯编号</summary>
          <p>
            <TermHelp
              term="research-method-version"
              label="分析规则"
            />
            ：{{ researchMethodLabel(plan.research.version) }}
          </p>
          <p class="help-text">内部规则编号：{{ plan.research.version }}</p>
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
            <strong>
              {{ item.date }} ·
              <TermHelp
                v-if="item.status === 'UNCHANGED'"
                term="plan-review-unchanged"
                :label="statuses[item.status]"
              />
              <template v-else>{{ statuses[item.status] || '待核对' }}</template>
            </strong>
            <p>{{ planReviewExplanations[item.status] || item.explanation }}</p>
            <p
              v-if="item.date === plan.research.asOf"
              class="help-text"
              data-testid="same-day-review-note"
            >
              本次复核日期与原研究日相同，尚未使用下一交易日的数据。
            </p>
            <p v-if="item.adjustedOriginalStop != null">
              <TermHelp
                term="original-risk-reference"
                label="原方案风险参考"
              />
              ：
              <strong class="reference-price">{{ number(item.adjustedOriginalStop) }} 元</strong>
              ·
              <TermHelp
                term="risk-price-adjustment"
                label="已统一价格口径"
              />
              <br />
              复核日收盘（{{ item.date }}）：{{ number(item.close) }} 元
            </p>
            <RiskReferenceGuide v-if="item.adjustedOriginalStop != null" />
            <details class="reading-details">
              <summary>查看原始复核记录与适用范围</summary>
              <p>{{ item.explanation }}</p>
              <p
                v-for="v in item.limitations"
                :key="v"
                class="help-text"
              >
                {{ v }}
              </p>
            </details>
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
.plan-focus-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.plan-focus-filters button {
  border: 1px solid #dce5ed;
  border-radius: 6px;
  padding: 8px 12px;
  background: #fff;
  color: #536a80;
  cursor: pointer;
}
.plan-focus-filters button[aria-pressed='true'] {
  background: #eaf2f4;
  border-color: #235970;
}
.plan-filters {
  display: grid;
  grid-template-columns: minmax(160px, 2fr) repeat(2, minmax(110px, 1fr)) auto;
  gap: 12px;
  margin: 20px 0;
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
