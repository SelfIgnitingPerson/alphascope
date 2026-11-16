<script setup lang="ts">
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import ReportFacts from '../components/reports/ReportFacts.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { computed, ref, watch, nextTick, useId } from 'vue';
import { getReport, getReportVersions, compareReports } from '../api/reports';
import { pretty, timestamp } from '../utils/format';
import type { ReportContent } from '../types/models';
const props = defineProps<PageProps & { reportId: number }>();
const { data, loading, error, reload } = useResource(
  (signal) => getReport(props.reportId, signal),
  {
    watch: [() => props.reportId, () => props.refreshToken],
    identity: () => String(props.reportId),
  },
);
const content = computed<ReportContent | undefined>(() => {
  const value = data.value?.content_json;
  if (!value) {
    return undefined;
  }
  try {
    return typeof value === 'string' ? (JSON.parse(value) as ReportContent) : value;
  } catch {
    return undefined;
  }
});
type ReportTab = 'overview' | 'stocks' | 'market' | 'text' | 'evidence';
const activeTab = ref<ReportTab>('overview');
const selectedSymbol = ref('');
const tabBar = ref<HTMLElement | null>(null);
const tabId = `report-reading-${useId()}`;
const tabs: { id: ReportTab; label: string }[] = [
  { id: 'overview', label: '重点概览' },
  { id: 'stocks', label: '个股研究' },
  { id: 'market', label: '市场与预测' },
  { id: 'text', label: '报告正文' },
  { id: 'evidence', label: '证据与版本' },
];
watch(
  () => data.value?.id,
  () => {
    selectedSymbol.value = data.value?.analysis?.stocks[0]?.symbol || '';
  },
);
async function openStock(symbol: string) {
  selectedSymbol.value = symbol;
  activeTab.value = 'stocks';
  await nextTick();
  tabBar.value?.scrollIntoView?.({ block: 'nearest' });
  tabBar.value
    ?.querySelector<HTMLButtonElement>('[aria-selected="true"]')
    ?.focus({ preventScroll: true });
}
async function moveTab(event: KeyboardEvent) {
  const index = tabs.findIndex((tab) => tab.id === activeTab.value);
  const next =
    event.key === 'ArrowRight'
      ? (index + 1) % tabs.length
      : event.key === 'ArrowLeft'
        ? (index + tabs.length - 1) % tabs.length
        : event.key === 'Home'
          ? 0
          : event.key === 'End'
            ? tabs.length - 1
            : -1;
  if (next < 0) return;
  event.preventDefault();
  activeTab.value = tabs[next]!.id;
  await nextTick();
  tabBar.value?.querySelector<HTMLButtonElement>('[aria-selected="true"]')?.focus();
}
const otherId = ref<number>();
watch(
  () => props.reportId,
  () => {
    otherId.value = undefined;
    activeTab.value = 'overview';
    selectedSymbol.value = '';
  },
);
const { data: versions } = useResource((signal) => getReportVersions(props.reportId, signal), {
  watch: [() => props.reportId],
  identity: () => String(props.reportId),
});
const { data: comparison, error: compareError } = useResource(
  (signal) =>
    otherId.value
      ? compareReports(props.reportId, otherId.value, signal)
      : Promise.resolve(undefined),
  { watch: [otherId, () => props.reportId], identity: () => `${props.reportId}:${otherId.value}` },
);
const newerVersion = computed(() =>
  versions.value?.find((version) => version.revision > (data.value?.revision ?? Infinity)),
);
const predatesResearchData = computed(() =>
  /(?:^|\/)REPORT_V[123]$/.test(data.value?.algorithm_version ?? ''),
);
const usefulFacts = computed(() => Boolean(data.value?.analysis?.stocks.length));
const visibleSections = computed(() => sections.filter(([key]) => content.value?.[key]?.length));
const sections: [
  keyof Pick<
    ReportContent,
    | 'positiveFactors'
    | 'negativeFactors'
    | 'themeOutlook'
    | 'watchlistRisks'
    | 'nextChecks'
    | 'uncertainties'
  >,
  string,
][] = [
  ['positiveFactors', '积极因素'],
  ['negativeFactors', '不利因素'],
  ['themeOutlook', '主题推断'],
  ['watchlistRisks', '个股风险'],
  ['nextChecks', '待验证事项'],
  ['uncertainties', '不确定性'],
];
</script>
<template>
  <div class="page-stack report-detail-page">
    <RouterLink
      to="/reports"
      class="back-link"
    >
      ← 返回报告归档
    </RouterLink>
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <template v-if="data">
        <div
          v-if="data.algorithm_version === 'LEGACY' || predatesResearchData || newerVersion"
          class="notice notice--subtle"
          role="note"
        >
          <strong>历史报告修订</strong>
          <span v-if="data.algorithm_version === 'LEGACY'">
            这是升级前生成的报告，部分证据字段当时尚未保存。
          </span>
          <span v-else-if="predatesResearchData">
            本修订生成于免费财报接入和自选研究模式升级之前，仍可能显示旧的缺失项。
            历史证据不会自动更新；需在后端升级后重新生成报告。
          </span>
          <RouterLink
            v-if="newerVersion"
            :to="'/reports/' + newerVersion.id"
            class="text-link"
          >
            打开第 {{ newerVersion.revision }} 版报告 →
          </RouterLink>
        </div>
        <div
          v-if="data.is_synthetic"
          class="notice notice--warning"
        >
          <strong>历史模拟报告</strong>
          <span>此报告使用演示数据，不代表真实市场。</span>
        </div>

        <article class="report-document">
          <header class="report-document__header">
            <span class="eyebrow">
              {{ data.report_type === 'DAILY' ? '收盘日报' : '每周周报' }} · 第
              {{ data.revision }} 版
            </span>
            <h2>
              {{ data.business_date }} {{ data.report_type === 'DAILY' ? '收盘日报' : '每周周报' }}
            </h2>
            <p>
              {{
                data.model === 'RULE_FALLBACK' ? '生成方式：规则版报告' : `生成模型：${data.model}`
              }}
              · 数据截至 {{ timestamp(data.data_as_of) }}
            </p>
          </header>
          <nav
            ref="tabBar"
            class="report-section-tabs"
            role="tablist"
            aria-label="报告内容分类"
            @keydown="moveTab"
          >
            <button
              v-for="tab in tabs"
              :key="tab.id"
              :id="`${tabId}-${tab.id}`"
              type="button"
              role="tab"
              :aria-selected="activeTab === tab.id"
              :aria-controls="`${tabId}-panel`"
              :tabindex="activeTab === tab.id ? 0 : -1"
              @click="activeTab = tab.id"
            >
              {{ tab.label }}
            </button>
          </nav>
          <section
            :id="`${tabId}-panel`"
            class="report-reading-panel"
            role="tabpanel"
            :aria-labelledby="`${tabId}-${activeTab}`"
            tabindex="0"
          >
            <template v-if="activeTab === 'overview'">
              <ReportFacts
                v-if="data.analysis && usefulFacts"
                :analysis="data.analysis"
                :weekly="data.report_type === 'WEEKLY'"
                :report-date="data.business_date"
                section="overview"
                @select-stock="openStock"
              />
              <div
                v-else
                class="report-summary"
              >
                <h3>本修订的文字摘要</h3>
                <p>{{ content?.summary || '本修订没有可解析的结构化研究内容。' }}</p>
                <p class="help-text">
                  缺少冻结行情时不补造个股事实；可切换到报告正文和证据查看原始归档。
                </p>
              </div>
            </template>
            <template v-if="activeTab === 'stocks'">
              <div v-if="data.analysis && usefulFacts">
                <div
                  class="report-stock-selector"
                  role="group"
                  aria-label="选择报告内股票"
                >
                  <button
                    v-for="stock in data.analysis.stocks"
                    :key="stock.symbol"
                    type="button"
                    :aria-pressed="selectedSymbol === stock.symbol"
                    @click="selectedSymbol = stock.symbol"
                  >
                    {{ stock.name }}
                    <small>{{ stock.symbol }}</small>
                  </button>
                </div>
                <ReportFacts
                  :analysis="data.analysis"
                  :weekly="data.report_type === 'WEEKLY'"
                  :report-date="data.business_date"
                  section="stocks"
                  :symbol="selectedSymbol"
                />
              </div>
              <p
                v-else
                class="help-text"
              >
                本修订没有冻结可用的个股事实；请查看归档正文或同日新修订。
              </p>
            </template>
            <template v-if="activeTab === 'market'">
              <ReportFacts
                v-if="data.analysis"
                :analysis="data.analysis"
                :weekly="data.report_type === 'WEEKLY'"
                :report-date="data.business_date"
                section="market"
              />
              <p
                v-if="
                  data.analysis &&
                  !data.analysis.marketObservation &&
                  data.analysis.market?.amount == null &&
                  !data.analysis.forecasts?.some((forecast) => forecast.latest)
                "
                class="help-text"
              >
                本修订未冻结可用的市场观察或机构预测，请核对缺项；不会读取当前数据补写历史。
              </p>
              <p
                v-else
                class="help-text"
              >
                本修订没有冻结市场观察或机构预测，不从当前接口补写历史。
              </p>
            </template>
            <section
              v-if="activeTab === 'text'"
              class="report-original-text"
            >
              <h3>归档文字与解读</h3>
              <div
                v-if="content"
                class="report-document__content"
              >
                <section class="report-summary">
                  <span class="eyebrow">研究摘要</span>
                  <p>{{ content.summary }}</p>
                </section>
                <section
                  v-for="[key, label] in visibleSections"
                  :key="key"
                  class="report-section"
                >
                  <h3>{{ label }}</h3>
                  <ul v-if="content[key]?.length">
                    <li
                      v-for="(item, index) in content[key]"
                      :key="index"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <p
                    v-else
                    class="muted"
                  >
                    本次没有可列示事项。
                  </p>
                </section>
              </div>
              <el-empty
                v-else
                description="报告内容不可解析，请重试或检查任务记录"
              />
            </section>
            <template v-if="activeTab === 'evidence'">
              <details class="report-evidence">
                <summary>本次冻结的完整输入证据</summary>
                <pre>{{ pretty(data.input_snapshot) }}</pre>
              </details>
              <details class="report-evidence">
                <summary>来源与证据（{{ data.sources.length }}项）</summary>
                <pre>{{ pretty(data.sources) }}</pre>
              </details>
              <section class="report-version-tools">
                <SectionCard title="版本与证据">
                  <p>
                    数据采集至 {{ timestamp(data.collected_at) }} · 算法
                    {{ data.algorithm_version || '历史版本' }} · {{ data.data_scope }}
                  </p>
                  <el-select
                    v-model="otherId"
                    placeholder="选择同日修订进行比较"
                    clearable
                  >
                    <el-option
                      v-for="version in versions?.filter((v) => v.id !== reportId) || []"
                      :key="version.id"
                      :label="'第 ' + version.revision + ' 版'"
                      :value="version.id"
                    />
                  </el-select>
                  <p
                    v-if="compareError"
                    role="alert"
                  >
                    {{ compareError }}
                  </p>
                  <div v-if="comparison">
                    <p>
                      输入证据{{ comparison.inputChanged ? '已变化' : '相同' }} ·
                      {{ comparison.changes.length }} 项内容变化
                    </p>
                    <article
                      v-for="change in comparison.changes"
                      :key="change.field"
                    >
                      <strong>
                        {{
                          change.field === 'summary'
                            ? '研究摘要'
                            : sections.find(([key]) => key === change.field)?.[1] || change.field
                        }}
                      </strong>
                      <div class="comparison-columns">
                        <div>
                          <small>当前修订</small>
                          <pre>{{ pretty(change.before) }}</pre>
                        </div>
                        <div>
                          <small>对比修订</small>
                          <pre>{{ pretty(change.after) }}</pre>
                        </div>
                      </div>
                    </article>
                  </div>
                </SectionCard>
              </section>
            </template>
          </section>
        </article>
      </template>
    </AsyncState>
  </div>
</template>
