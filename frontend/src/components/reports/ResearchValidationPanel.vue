<script setup lang="ts">
import ValidationMarketPanel from './ValidationMarketPanel.vue';
import StrategySimulationPanel from './StrategySimulationPanel.vue';
import { computed, nextTick, onScopeDispose, ref, watch } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import StateBadge from '../common/StateBadge.vue';
import { useMutation } from '../../composables/useMutation';
import { useResource } from '../../composables/useResource';
import { getWatchlist } from '../../api/portfolio';
import {
  cancelValidation,
  getValidationTask,
  getValidationTasks,
  submitValidation,
  validationEvidenceUrl,
} from '../../api/validation';
import type { ResearchValidationRequest, ValidationTask } from '../../types/validation';
import { percent, ruleNames, timestamp } from '../../utils/format';
import { yearBefore } from '../../utils/date';

const props = defineProps<{
  date: string;
  synthetic: boolean;
  preferredSymbol?: string;
  refreshToken?: number;
}>();
const emit = defineEmits<{ select: [symbol: string] }>();
const formOpen = ref(false);
const formElement = ref<HTMLDetailsElement>();
const start = ref(yearBefore(yearBefore(props.date)));
const end = ref(props.date);
const scope = ref<ResearchValidationRequest['scope']>('ALL');
const symbols = ref<string[]>([]);
const selected = ref<ValidationTask | null>(null);
const stock = ref('');
const period = ref('ALL');
const horizon = ref('5D');
const notice = ref('');
const readError = ref('');
const loadingTask = ref(false);
const { busy, error, run } = useMutation();
const history = useResource(getValidationTasks, []);
const watchlist = useResource(getWatchlist, [() => props.refreshToken]);
const watchedStocks = computed(() => (watchlist.data.value || []).filter((item) => item.enabled));
const active = (status: string) =>
  ['PENDING', 'RUNNING', 'CANCEL_REQUESTED', 'TIMEOUT_REQUESTED'].includes(status);
const running = computed(() => selected.value && active(selected.value.status));
const summary = computed(() => selected.value?.summary);
const selectedStock = computed(() =>
  summary.value?.stocks.find((item) => item.symbol === stock.value),
);
const rows = computed(() => {
  const result = selectedStock.value?.reconstructed;
  const statistics =
    period.value === 'ALL'
      ? result?.statistics
      : result?.validationWindows?.find((window) => window.label === period.value)?.statistics;
  return Object.entries(statistics?.[horizon.value] || {}).map(([rule, value]) => ({
    rule,
    ...value,
  }));
});

let timer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
let selectionSequence = 0;
watch(running, (value) => {
  clearTimeout(timer);
  if (value) schedule();
});
watch([watchedStocks, () => props.preferredSymbol], ([items, preferred]) => {
  if (!watchlist.data.value) return;
  if (items.some((item) => item.symbol === preferred)) stock.value = preferred || '';
  else if (!items.some((item) => item.symbol === stock.value)) stock.value = items[0]?.symbol || '';
});
watch(stock, (symbol) => {
  emit('select', symbol);
  showSavedStock();
});
watch(history.data, (page) => {
  if (!selected.value && page?.items.length) {
    void selectTask(
      (
        page.items.find(
          (item) =>
            item.symbols?.includes(stock.value) &&
            (active(item.status) || item.status === 'SUCCESS'),
        ) || page.items[0]
      ).id,
    );
  }
  if (!selected.value && page && !page.items.length) formOpen.value = true;
});
function showSavedStock() {
  if (!stock.value || selectedStock.value) return;
  const task = history.data.value?.items.find(
    (item) => item.status === 'SUCCESS' && item.symbols?.includes(stock.value),
  );
  if (task && task.id !== selected.value?.id) void selectTask(task.id);
}
function onFormToggle(event: Event) {
  formOpen.value = (event.target as HTMLDetailsElement).open;
}
async function openForm() {
  formOpen.value = true;
  await nextTick();
  formElement.value?.scrollIntoView?.({ block: 'start' });
}
function prepareUpdate() {
  start.value = summary.value?.start || yearBefore(yearBefore(props.date));
  end.value = props.date;
  scope.value = 'TECHNICAL';
  symbols.value = stock.value ? [stock.value] : [];
  void openForm();
}
onScopeDispose(() => {
  disposed = true;
  selectionSequence++;
  clearTimeout(timer);
});
function schedule() {
  clearTimeout(timer);
  timer = setTimeout(async () => {
    const id = selected.value?.id;
    if (!id || disposed) return;
    try {
      const value = await getValidationTask(id);
      if (!disposed && selected.value?.id === id) selected.value = value;
      readError.value = '';
      await history.reload();
    } catch (reason) {
      readError.value = reason instanceof Error ? reason.message : String(reason);
    }
    if (!disposed && running.value) schedule();
  }, 2000);
}
async function selectTask(id: number) {
  const sequence = ++selectionSequence;
  loadingTask.value = true;
  clearTimeout(timer);
  try {
    const value = await getValidationTask(id);
    if (disposed || sequence !== selectionSequence) return;
    selected.value = value;
    readError.value = '';
    formOpen.value = false;
    if (running.value) schedule();
  } catch (reason) {
    if (!disposed && sequence === selectionSequence)
      readError.value = reason instanceof Error ? reason.message : String(reason);
  } finally {
    if (sequence === selectionSequence) loadingTask.value = false;
  }
}
async function submit() {
  await run(async () => {
    if (props.synthetic) throw Error('演示模式不能提交真实研究验证');
    if (!start.value || !end.value || start.value > end.value)
      throw Error('请填写顺序正确的起止日期');
    selectionSequence++;
    loadingTask.value = false;
    selected.value = await submitValidation({
      start: start.value,
      end: end.value,
      scope: scope.value,
      symbols: scope.value === 'MARKET' ? [] : symbols.value,
    });
    formOpen.value = false;
    notice.value = '验证任务已接收；计算在后台执行，刷新页面不会重复提交。';
    await history.reload();
  });
}
async function cancel() {
  const id = selected.value?.id;
  if (!id) return;
  selectionSequence++;
  loadingTask.value = false;
  await run(async () => {
    selected.value = await cancelValidation(id);
    await history.reload();
  });
}
</script>

<template>
  <SectionCard
    title="历史技术规律（逐日重建）"
    description="先读取已完成的区间验证，查看技术规则触发后的真实样本；新建任务只使用本机已有行情，不自动下载。"
  >
    <template #actions>
      <el-button @click="openForm">新建验证</el-button>
      <el-button
        @click="
          () => {
            history.reload();
            watchlist.reload();
          }
        "
      >
        刷新自选与记录
      </el-button>
    </template>
    <el-alert
      v-if="error || readError || history.error.value || watchlist.error.value"
      :title="error || readError || history.error.value || watchlist.error.value"
      type="error"
      :closable="false"
    />
    <div class="filter-bar filter-bar--compact">
      <label class="filter-field">
        验证证券（当前自选）
        <el-select
          v-model="stock"
          filterable
          :loading="watchlist.loading.value"
          :disabled="!watchedStocks.length"
          placeholder="请先添加自选股"
          aria-label="验证结果股票"
        >
          <el-option
            v-for="item in watchedStocks"
            :key="item.symbol"
            :value="item.symbol"
            :label="`${item.name} ${item.symbol}`"
          />
        </el-select>
      </label>
      <span class="help-text">
        来自当前启用的自选股，共 {{ watchedStocks.length }} 只；刷新可同步最新关注范围。
      </span>
    </div>
    <p
      v-if="!watchlist.loading.value && !watchlist.error.value && !watchedStocks.length"
      class="query-note"
      data-testid="validation-no-watches"
    >
      暂无启用的自选股，请先到
      <router-link to="/watchlist">自选股</router-link>
      添加关注股票。旧验证记录仍保留。
    </p>
    <div
      v-if="stock && !selectedStock && !loadingTask && !history.loading.value"
      class="query-note"
      data-testid="validation-stock-missing"
    >
      <strong>{{ stock }} 尚未包含在当前验证结果中。</strong>
      <span>选择自选股不会自动下载或生成数据；可使用已保存行情为这只股票建立验证。</span>
      <el-button
        link
        type="primary"
        :disabled="synthetic || busy"
        @click="prepareUpdate"
      >
        准备本股验证 →
      </el-button>
    </div>
    <p
      v-if="loadingTask"
      class="help-text"
      role="status"
    >
      正在读取所选股票的已有验证记录…
    </p>
    <div
      v-if="selected"
      class="evidence-block"
      data-testid="validation-task-detail"
    >
      <div class="button-row">
        <strong>验证任务 #{{ selected.id }}</strong>
        <StateBadge :value="selected.status" />
        <el-button
          v-if="running"
          :disabled="busy || selected.status === 'CANCEL_REQUESTED'"
          data-testid="cancel-validation"
          @click="cancel"
        >
          取消验证
        </el-button>
        <a
          v-if="selected.status === 'SUCCESS' && selected.evidenceSha256"
          :href="validationEvidenceUrl(selected.id)"
          download
          data-testid="download-validation"
        >
          下载完整证据 JSON
        </a>
      </div>
      <p v-if="!summary">
        {{ selected.start }} 至 {{ selected.end }} ·
        {{ selected.symbols?.join('、') || '完整市场' }}
      </p>
      <p v-if="!summary">
        {{ selected.message }} · {{ selected.completed }} / {{ selected.total || '待确定' }}
      </p>
      <p
        v-if="!summary"
        class="help-text"
      >
        心跳：{{ timestamp(selected.heartbeatAt) }} · 截止：{{ timestamp(selected.deadlineAt) }} ·
        数据范围：{{ selected.dataScope }}
      </p>
    </div>
    <div
      v-if="summary"
      class="page-stack"
      data-testid="validation-summary"
    >
      <div class="query-note">
        <span data-testid="validation-frozen-range">
          本次验证区间：{{ summary.start }} 至
          {{ summary.end }}。这是已保存结果，不随顶部研究日期自动重算。
        </span>
        <el-button
          v-if="summary.end !== date"
          link
          type="primary"
          @click="prepareUpdate"
        >
          使用所选股票更新至 {{ date }} →
        </el-button>
      </div>
      <ValidationMarketPanel :summary="summary" />
      <template v-if="selectedStock">
        <StrategySimulationPanel :simulation="selectedStock.strategy" />
        <h3>技术规则回顾</h3>
        <div class="filter-bar filter-bar--compact">
          <label class="filter-field">
            验证区间
            <el-select
              v-model="period"
              aria-label="验证结果区间"
            >
              <el-option
                label="较晚留出"
                value="HOLDOUT"
              />
              <el-option
                label="较早区间"
                value="EARLY"
              />
              <el-option
                label="全部区间"
                value="ALL"
              />
            </el-select>
          </label>
          <label class="filter-field">
            收益窗口
            <el-select
              v-model="horizon"
              aria-label="收益窗口"
            >
              <el-option
                v-for="value in ['5D', '10D', '20D']"
                :key="value"
                :value="value"
                :label="value"
              />
            </el-select>
          </label>
        </div>
        <p
          v-if="selectedStock"
          class="help-text"
          data-testid="validation-stock-context"
        >
          实际行情 {{ selectedStock.firstPriceDate }} 至 {{ selectedStock.lastPriceDate }}，{{
            selectedStock.priceDays
          }}天；留出起点 {{ selectedStock.reconstructed?.splitDate || '无' }}。{{
            selectedStock.failure
          }}
        </p>
        <el-table
          :data="rows"
          empty-text="本区间没有可验证记录"
        >
          <el-table-column
            prop="triggeredCount"
            label="触发记录"
          />
          <el-table-column
            label="风险规则"
            min-width="160"
          >
            <template #default="s">{{ ruleNames[s.row.rule] || s.row.rule }}</template>
          </el-table-column>
          <el-table-column
            prop="sampleCount"
            label="样本"
          />
          <el-table-column label="触发后收益均值">
            <template #default="s">{{ percent(s.row.meanReturn, 2) }}</template>
          </el-table-column>
          <el-table-column label="超额均值">
            <template #default="s">{{ percent(s.row.meanExcessReturn, 2) }}</template>
          </el-table-column>
          <el-table-column label="状态">
            <template #default="s"><StateBadge :value="s.row.dataStatus" /></template>
          </el-table-column>
        </el-table>
      </template>
      <details class="reading-details">
        <summary>
          复算与方法边界
          <span>
            {{
              summary.verification.matched
                ? '价格、指标、规则、统计复算一致；不等于已证明有效'
                : '证据尚未核对'
            }}
          </span>
        </summary>
        <p>
          {{ summary.ruleDefinition.ruleVersion }} · {{ summary.ruleDefinition.technicalVersion }}
        </p>
        <ul class="help-text">
          <li
            v-for="item in [...summary.blockers, ...summary.limitations]"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
      </details>
    </div>
    <details
      ref="formElement"
      class="reading-details"
      :open="formOpen"
      data-testid="validation-create"
      @toggle="onFormToggle"
    >
      <summary>
        新建或更新区间验证
        <span>选择股票和日期后显式提交，不会因刷新自动计算</span>
      </summary>
      <form
        class="validation-form"
        data-testid="validation-form"
        @submit.prevent="submit"
      >
        <label
          class="validation-form__field"
          for="validation-start"
        >
          开始日期
          <el-date-picker
            id="validation-start"
            v-model="start"
            type="date"
            aria-label="验证开始日期"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :clearable="false"
          />
        </label>
        <label
          class="validation-form__field"
          for="validation-end"
        >
          截止日期
          <el-date-picker
            id="validation-end"
            v-model="end"
            type="date"
            aria-label="验证截止日期"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :clearable="false"
          />
        </label>
        <label class="validation-form__field">
          验证类型
          <el-select
            v-model="scope"
            aria-label="验证类型"
          >
            <el-option
              label="全部研究验证"
              value="ALL"
            />
            <el-option
              label="五条技术规则"
              value="TECHNICAL"
            />
            <el-option
              label="完整市场评分"
              value="MARKET"
            />
          </el-select>
        </label>
        <label class="validation-form__field validation-form__symbols">
          验证证券
          <el-select
            v-model="symbols"
            multiple
            :multiple-limit="10"
            :disabled="scope === 'MARKET'"
            placeholder="留空使用已启用自选"
            aria-label="验证证券"
          >
            <el-option
              v-for="item in (watchlist.data.value || []).filter((row) => row.enabled)"
              :key="item.symbol"
              :value="item.symbol"
              :label="`${item.name} ${item.symbol}`"
            />
          </el-select>
        </label>
        <el-button
          native-type="submit"
          type="primary"
          class="validation-form__submit"
          :disabled="synthetic || busy"
          data-testid="submit-validation"
        >
          提交验证
        </el-button>
      </form>
      <p class="help-text">
        研究区间最多十年，指标预热另行读取；范围与阈值在提交时冻结。当前自选存在选择偏差，结果不直接推广至全A。
      </p>
      <el-alert
        v-if="synthetic"
        title="当前为演示模式，禁止提交真实效果验证；已有记录仍可读取。"
        type="info"
        :closable="false"
      />

      <p
        v-if="notice"
        role="status"
      >
        {{ notice }}
      </p>
    </details>
    <details class="reading-details">
      <summary>
        验证任务记录（{{ history.data.value?.items.length || 0 }} 项）
        <span>展开后选择要核对的任务</span>
      </summary>
      <el-table
        :data="history.data.value?.items || []"
        empty-text="尚未提交研究验证"
      >
        <el-table-column
          prop="id"
          label="任务"
          width="80"
        />
        <el-table-column
          label="研究区间"
          min-width="220"
        >
          <template #default="s">{{ s.row.start }} 至 {{ s.row.end }}</template>
        </el-table-column>
        <el-table-column
          label="范围"
          min-width="130"
        >
          <template #default="s">
            {{
              s.row.scope === 'ALL'
                ? '全部研究'
                : s.row.scope === 'TECHNICAL'
                  ? '技术规则'
                  : s.row.scope === 'MARKET'
                    ? '完整市场'
                    : s.row.scope
            }}
          </template>
        </el-table-column>
        <el-table-column label="状态">
          <template #default="s"><StateBadge :value="s.row.status" /></template>
        </el-table-column>
        <el-table-column
          prop="message"
          label="进度或结果"
          min-width="260"
        />
        <el-table-column
          label="提交时间"
          min-width="170"
        >
          <template #default="s">{{ timestamp(s.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
        >
          <template #default="s">
            <el-button
              link
              @click="selectTask(s.row.id)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </details>
  </SectionCard>
</template>

<style scoped>
/* 与工作台日期选择和Element Plus输入统一，独立布局避免inline-form的窄宽度限制。 */
.validation-form {
  display: grid;
  grid-template-columns:
    minmax(145px, 1fr) minmax(145px, 1fr) minmax(160px, 1fr) minmax(220px, 1.5fr)
    auto;
  align-items: end;
  gap: 16px;
  margin: 8px 0 20px;
}
.validation-form__field {
  display: grid;
  gap: 8px;
  min-width: 0;
  color: var(--el-text-color-secondary, #738398);
  font-size: 12px;
  line-height: 1.5;
}
.validation-form :deep(.el-date-editor.el-input),
.validation-form :deep(.el-select) {
  width: 100%;
  min-width: 0;
}
.validation-form__submit {
  height: 32px;
  margin-left: 0;
}
@media (max-width: 1200px) {
  .validation-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .validation-form__submit {
    grid-column: 2;
    justify-self: end;
  }
}
@media (max-width: 560px) {
  .validation-form {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }
  .validation-form__submit {
    grid-column: 1;
    justify-self: stretch;
  }
}
</style>
