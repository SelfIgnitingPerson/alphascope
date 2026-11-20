<script setup lang="ts">
import ForwardValidationPanel from '../components/research/ForwardValidationPanel.vue';
import SectionTabs from '../components/common/SectionTabs.vue';
import TermHelp from '../components/common/TermHelp.vue';
import { ruleTermIds } from '../content/research-terms';
import ValidationExperimentPanel from '../components/reports/ValidationExperimentPanel.vue';
import ResearchValidationPanel from '../components/reports/ResearchValidationPanel.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { ref, computed, watch } from 'vue';
import { getBacktest } from '../api/market';
import { yearBefore } from '../utils/date';
import { percent, ruleNames } from '../utils/format';
import { normalizeStockSymbol } from '../utils/stock-symbol';
const props = defineProps<PageProps>();
const activeTab = ref('validation');
const tabs = [
  {
    id: 'validation',
    label: '历史规律验证',
    description:
      '用已保存的历史行情逐日重建技术规则，先看实际样本与后续表现；结果保留各自的冻结日期。',
  },
  {
    id: 'signals',
    label: '已保存信号',
    description: '只核对运行以来实际保存的规则记录；不会自动补算过去的信号。',
  },
  {
    id: 'experiments',
    label: '复算归档',
    description: '保存当前查询的验证实验，或核对已冻结的历史输入。',
  },
  {
    id: 'forward',
    label: '真实前瞻跟踪',
    description: '只统计实际当日形成的冻结判断，扣费、未成交和样本边界分别核验。',
  },
  { id: 'method', label: '方法说明', description: '了解统计口径、样本排除与结果的适用边界。' },
];
const symbol = ref('');
const availabilityMode = ref<'RETROSPECTIVE' | 'POINT_IN_TIME'>('RETROSPECTIVE');
const selectedWindow = ref('ALL');
const ruleVersion = ref('');
const requestedVersion = ref('');
const formError = ref('');
const horizon = ref('5D');
const triggeredOnly = ref(true);
const exclusionNames: Record<string, string> = {
  NOT_TRIGGERED: '未明确触发',
  WAITING_WINDOW: '未来交易窗口尚未成熟',
  CALENDAR_MISSING: '缺少权威交易日历',
  OVERLAPPING_SIGNAL: '与已选窗口重叠',
  PRICE_OR_FACTOR_GAP: '连续原价或因子缺失',
  SIGNAL_NOT_AVAILABLE_AS_OF: '信号当时不可得或版本未知',
  PRICE_NOT_AVAILABLE_AS_OF: '行情当时不可得或后来修订',
  MIXED_RULE_VERSIONS: '同规则包含多个版本',
  OTHER_RULE_VERSION: '不属于指定版本',
  SYNTHETIC: '演示记录',
};
const requestedSymbol = ref(symbol.value);
const draftChanged = computed(
  () =>
    normalizeStockSymbol(symbol.value) !== requestedSymbol.value ||
    ruleVersion.value.trim() !== requestedVersion.value,
);
const request = computed(() => ({
  symbol: requestedSymbol.value,
  start: yearBefore(props.date),
  end: props.date,
  availabilityMode: availabilityMode.value,
  ruleVersion: requestedVersion.value || undefined,
}));
const { data, loading, error, reload } = useResource(
  (signal) =>
    request.value.symbol
      ? getBacktest(request.value, signal)
      : Promise.resolve({
          dataStatus: 'INSUFFICIENT_DATA',
          reason: '请先选择自选股，或输入股票代码查询。',
          statistics: {},
        }),
  {
    watch: [
      requestedSymbol,
      requestedVersion,
      availabilityMode,
      () => props.date,
      () => props.refreshToken,
    ],
    identity: () => JSON.stringify(request.value),
  },
);
watch(request, () => {
  selectedWindow.value = 'ALL';
});
const rows = computed(() =>
  Object.entries(
    (selectedWindow.value === 'ALL'
      ? data.value?.statistics
      : data.value?.validationWindows?.find((window) => window.label === selectedWindow.value)
          ?.statistics) || {},
  )
    .filter(([window]) => window === horizon.value)
    .flatMap(([window, rules]) =>
      Object.entries(rules)
        .filter(([, stats]) => !triggeredOnly.value || (stats.triggeredCount || 0) > 0)
        .map(([rule, stats]) => ({ window, rule, ...stats })),
    ),
);
const versions = computed(() =>
  [
    ...new Set(
      Object.values(data.value?.statistics?.['5D'] || {}).flatMap((s) => s.ruleVersions || []),
    ),
  ].sort(),
);
const mixedVersions = computed(() =>
  Object.values(data.value?.statistics?.['5D'] || {}).some(
    (s) => (s.exclusions?.MIXED_RULE_VERSIONS || 0) > 0,
  ),
);
function exclusionSummary(exclusions: Record<string, number> | undefined, triggered: number) {
  return (
    Object.entries(exclusions || {})
      .filter(([reason]) => reason !== 'NOT_TRIGGERED')
      .map(([reason, count]) => `${exclusionNames[reason] || reason} ${count} 条`)
      .join('；') || (triggered > 0 ? '无额外排除项' : '该规则没有明确触发记录')
  );
}
function search() {
  formError.value = '';
  const value = normalizeStockSymbol(symbol.value);
  if (!value) {
    formError.value = '请输入有效股票代码，例如 002837、600000.SH 或 830799.BJ。';
    return;
  }
  symbol.value = value;
  if (value === requestedSymbol.value && requestedVersion.value === ruleVersion.value.trim()) {
    void reload();
  } else {
    requestedSymbol.value = value;
    requestedVersion.value = ruleVersion.value.trim();
  }
}
function selectWatchSymbol(value: string) {
  if (value === requestedSymbol.value) return;
  symbol.value = value;
  requestedSymbol.value = value;
  ruleVersion.value = '';
  requestedVersion.value = '';
  formError.value = '';
}
</script>
<template>
  <SectionTabs
    v-model="activeTab"
    label="历史信号内容分类"
    :items="tabs"
  >
    <template #signals>
      <SectionCard
        title="已保存信号的后续表现"
        description="这里统计实际保存的触发记录，历史K线不会自动变成历史信号；按版本与收益窗口分别核对。"
      >
        <form
          class="filter-bar"
          @submit.prevent="search"
        >
          <label
            class="filter-field"
            for="signal-symbol"
          >
            股票代码
            <el-input
              id="signal-symbol"
              v-model="symbol"
              placeholder="例如 002837 或 300476.SZ"
              aria-label="查询股票代码"
            />
          </label>
          <label
            class="filter-field"
            for="signal-version"
          >
            规则版本（分开统计）
            <el-select
              id="signal-version"
              v-model="ruleVersion"
              placeholder="选择已保存版本"
              aria-label="规则版本或阈值指纹"
              filterable
              clearable
              allow-create
            >
              <el-option
                v-for="version in versions"
                :key="version"
                :label="version"
                :value="version"
              />
            </el-select>
          </label>
          <label class="filter-field">
            收益观察窗口
            <el-select
              v-model="horizon"
              aria-label="信号收益窗口"
            >
              <el-option
                v-for="window in ['5D', '10D', '20D']"
                :key="window"
                :value="window"
                :label="`${window.replace('D', '')}个交易日`"
              />
            </el-select>
          </label>
          <label
            class="filter-field"
            for="signal-availability"
          >
            可用时点
            <el-select
              id="signal-availability"
              v-model="availabilityMode"
              aria-label="可用时点"
            >
              <el-option
                label="当前版本回顾"
                value="RETROSPECTIVE"
              />
              <el-option
                label="严格历史时点"
                value="POINT_IN_TIME"
              />
            </el-select>
          </label>
          <label
            class="filter-field"
            for="signal-window"
          >
            验证区间
            <el-select
              id="signal-window"
              v-model="selectedWindow"
              aria-label="验证区间"
            >
              <el-option
                label="全部区间"
                value="ALL"
              />
              <el-option
                label="较早区间"
                value="EARLY"
              />
              <el-option
                label="较晚留出区间"
                value="HOLDOUT"
              />
            </el-select>
          </label>
          <el-button
            type="primary"
            native-type="submit"
          >
            查询信号
          </el-button>
        </form>
        <p
          v-if="formError"
          class="error-text"
          role="alert"
        >
          {{ formError }}
        </p>
        <p
          v-if="draftChanged"
          class="query-note"
          role="status"
          data-testid="signal-query-pending"
        >
          输入条件尚未查询，下面仍是 {{ requestedSymbol }} 的已提交结果。请点击“查询信号”。
        </p>
        <AsyncState
          :loading="loading"
          :error="error"
          @retry="reload"
        >
          <div
            class="notice notice--subtle"
            data-testid="saved-signal-guidance"
          >
            <strong>{{ data?.reason || '以下是已保存信号的实际统计。' }}</strong>
            <span v-if="mixedVersions">
              同规则存在多个版本，请从上方选择一个版本再查询，不能直接合并。
            </span>
            <span>
              想查看较长历史中的技术规律，请使用逐日重建；未保存的财报、主题和持仓不能据此补造。
            </span>
            <el-button
              link
              type="primary"
              @click="activeTab = 'validation'"
            >
              查看历史规律验证 →
            </el-button>
          </div>
          <p class="query-note">
            {{
              availabilityMode === 'POINT_IN_TIME'
                ? '严格模式：只接受历史时点已可得且版本可验证的记录。'
                : '当前版本回顾：采用现有已保存版本，不证明历史时点可得。'
            }}
            · 收益未扣交易成本。
          </p>
          <div class="table-caption">
            <span>{{ requestedSymbol }} · {{ yearBefore(date) }} 至 {{ date }}</span>
            <StateBadge :value="data?.dataStatus" />
          </div>
          <el-checkbox
            v-model="triggeredOnly"
            aria-label="只看已触发规则"
          >
            只看已触发规则
          </el-checkbox>
          <el-table
            :data="rows"
            empty-text="当前筛选没有触发规则；可取消“只看已触发规则”核对全部记录，或查看历史规律验证。"
          >
            <el-table-column type="expand">
              <template #default="s">
                <div class="evidence-block">
                  <p>
                    保存记录 {{ s.row.recordCount || 0 }} 条；明确触发
                    {{ s.row.triggeredCount || 0 }} 条；有效基准
                    {{ s.row.benchmarkSampleCount || 0 }} 个。
                  </p>
                  <dl class="detail-metrics">
                    <div>
                      <dt>收益中位数</dt>
                      <dd>{{ percent(s.row.medianReturn, 2) }}</dd>
                    </div>
                    <div>
                      <dt>基准均值</dt>
                      <dd>{{ percent(s.row.meanBenchmarkReturn, 2) }}</dd>
                    </div>
                  </dl>
                  <p>规则版本：{{ s.row.ruleVersions?.join('、') || '未记录' }}</p>
                  <ul>
                    <li
                      v-for="(count, reason) in s.row.exclusions"
                      :key="reason"
                    >
                      {{ exclusionNames[String(reason)] || reason }}：{{ count }}
                    </li>
                  </ul>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="triggeredCount"
              label="触发记录"
              min-width="90"
            />
            <el-table-column
              label="规则"
              min-width="160"
            >
              <template #default="s">
                <TermHelp
                  :term="ruleTermIds[s.row.rule]"
                  :label="ruleNames[s.row.rule] || s.row.rule"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="window"
              label="观察窗口"
              min-width="100"
            />
            <el-table-column
              prop="sampleCount"
              label="样本数"
              min-width="80"
            />
            <el-table-column
              label="平均收益"
              min-width="115"
            >
              <template #header>
                <TermHelp
                  term="return-statistics"
                  label="平均收益"
                />
              </template>
              <template #default="s">{{ percent(s.row.meanReturn, 2) }}</template>
            </el-table-column>
            <el-table-column
              label="超额均值"
              min-width="115"
            >
              <template #header>
                <TermHelp
                  term="return-statistics"
                  label="超额均值"
                />
              </template>
              <template #default="s">{{ percent(s.row.meanExcessReturn, 2) }}</template>
            </el-table-column>
            <el-table-column
              label="样本状态"
              min-width="130"
            >
              <template #default="s"><StateBadge :value="s.row.dataStatus" /></template>
            </el-table-column>
            <el-table-column
              label="正收益占比"
              min-width="115"
            >
              <template #header>
                <TermHelp
                  term="positive-ratio"
                  label="正收益占比"
                />
              </template>
              <template #default="s">{{ percent(s.row.positiveRatio) }}</template>
            </el-table-column>
            <el-table-column
              label="未计入样本的原因"
              min-width="230"
            >
              <template #default="s">
                {{ exclusionSummary(s.row.exclusions, s.row.triggeredCount || s.row.sampleCount) }}
              </template>
            </el-table-column>
          </el-table>
          <p
            v-if="data?.reason"
            class="help-text"
          >
            {{ data.reason }}
          </p>
          <p class="help-text">
            展开行首箭头可查看收益中位数、基准均值、规则版本与排除原因；完整统计口径见“方法说明”。
          </p>
        </AsyncState>
      </SectionCard>
    </template>
    <template #forward>
      <ForwardValidationPanel
        :date="date"
        :refresh-token="refreshToken"
      />
    </template>
    <template #validation>
      <ResearchValidationPanel
        :date="date"
        :synthetic="data?.dataStatus === 'SYNTHETIC'"
        :preferred-symbol="requestedSymbol"
        :refresh-token="refreshToken"
        @select="selectWatchSymbol"
      />
    </template>
    <template #experiments>
      <div class="query-note">
        当前查询：{{ requestedSymbol }} · {{ request.start }} 至 {{ request.end }} ·
        {{ availabilityMode === 'POINT_IN_TIME' ? '严格历史时点' : '当前版本回顾' }} · 规则版本：{{
          requestedVersion || '未指定'
        }}。修改查询请返回“信号回顾”。
      </div>
      <ValidationExperimentPanel
        :request="request"
        :synthetic="data?.dataStatus === 'SYNTHETIC'"
      />
    </template>
    <template #method>
      <SectionCard
        title="统计口径与适用边界"
        description="结果反映选定样本的历史表现；复算一致与方法有效是两项独立检查。"
      >
        <dl class="reading-definitions">
          <div>
            <dt>收益口径</dt>
            <dd>
              收盘到收盘、未扣交易成本，不考虑涨跌停与可成交性。历史结果不代表未来收益，模拟数据不参与真实效果统计。
            </dd>
          </div>
          <div>
            <dt>区间划分</dt>
            <dd>
              留出区间首日：{{
                data?.splitDate || '区间不足以划分'
              }}；较早区间的收益不能跨越留出起点。
            </dd>
          </div>
          <div>
            <dt>样本门槛</dt>
            <dd>低于 {{ data?.minimumSamples || 20 }} 个样本提示小样本，不能据此推断稳定效果。</dd>
          </div>
          <div>
            <dt>可用时点</dt>
            <dd>
              当前版本回顾采用现有已保存版本。严格历史时点模式拒绝回填、时间未知或后来修订的信号和行情；没有可验证记录时保持无样本。
            </dd>
          </div>
        </dl>
        <ul class="help-text">
          <li
            v-for="limit in data?.limitations"
            :key="limit"
          >
            {{ limit }}
          </li>
        </ul>
      </SectionCard>
    </template>
  </SectionTabs>
</template>
