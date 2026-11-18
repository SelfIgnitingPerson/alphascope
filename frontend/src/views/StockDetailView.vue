<script setup lang="ts">
import HoldingResearchPanel from '../components/research/HoldingResearchPanel.vue';
import FinancialResearchPanel from '../components/research/FinancialResearchPanel.vue';
import SectionTabs from '../components/common/SectionTabs.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import TermHelp from '../components/common/TermHelp.vue';
import RuleStatusGuide from '../components/common/RuleStatusGuide.vue';
import { ruleTermIds } from '../content/research-terms';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { computed, ref, watch } from 'vue';
import PriceChart from '../components/charts/PriceChart.vue';
import { getInstrument, getBars, getIndicators, getFinancials, getSignals } from '../api/market';
import { yearBefore } from '../utils/date';
import { number, percent, pretty, sourceName, timestamp, ruleNames } from '../utils/format';
const props = defineProps<PageProps & { symbol: string; initialHorizon?: string }>();
// 主资料、行情、指标、财报与规则各自加载；可选资料故障不能遮住其余研究。
const query = {
  watch: [() => props.symbol, () => props.date, () => props.refreshToken],
  identity: () => `${props.symbol}:${props.date}`,
};
const {
  data: stock,
  loading,
  error,
  reload,
} = useResource((signal) => getInstrument(props.symbol, signal), {
  watch: [() => props.symbol, () => props.refreshToken],
  identity: () => props.symbol,
});
const bars = useResource(
  (signal) => getBars(props.symbol, { start: yearBefore(props.date), end: props.date }, signal),
  query,
);
const indicators = useResource(
  (signal) =>
    getIndicators(props.symbol, { start: yearBefore(props.date), end: props.date }, signal),
  query,
);
const financials = useResource((signal) => getFinancials(props.symbol, props.date, signal), query);
const signals = useResource((signal) => getSignals(props.symbol, props.date, signal), query);
const indicatorColumns = ['ma5', 'ma10', 'ma20', 'ma60', 'atr14'] as const;
const activeTab = ref('plans');
const tabs = [
  { id: 'plans', label: '持有与买卖计划' },
  { id: 'price', label: '价格与指标' },
  { id: 'rules', label: '风险规则' },
  { id: 'financials', label: '已披露财报' },
];
const signalDate = ref('');
watch(
  () => props.date,
  () => {
    signalDate.value = '';
  },
);
const signalDates = computed(() =>
  [...new Set(signals.data.value?.map((row) => row.trade_date) || [])].sort().reverse(),
);
const visibleSignals = computed(() => {
  const date = signalDates.value.includes(signalDate.value)
    ? signalDate.value
    : signalDates.value[0];
  return signals.data.value?.filter((row) => row.trade_date === date) || [];
});
const latest = computed(() => bars.data.value?.items.at(-1));
const indicator = computed(() => indicators.data.value?.at(-1));
</script>
<template>
  <div class="page-stack">
    <RouterLink
      to="/watchlist"
      class="back-link"
    >
      ← 返回自选股
    </RouterLink>
    <AsyncState
      :loading="loading"
      :error="error"
      :has-content="Boolean(stock)"
      @retry="reload"
    >
      <template v-if="stock">
        <div class="stock-heading">
          <div>
            <span class="eyebrow">{{ symbol }}</span>
            <h2>{{ stock.name }}</h2>
            <p class="muted">
              {{ sourceName(latest?.source) }} · 行情 {{ latest?.trade_date || '暂无' }} · 采集
              {{ timestamp(latest?.fetched_at) }}
            </p>
          </div>
          <div
            class="stock-heading__price"
            :aria-busy="bars.loading.value"
          >
            <strong>{{ bars.loading.value && !latest ? '加载中' : number(latest?.close) }}</strong>
            <span>收盘价 / 元</span>
          </div>
        </div>
        <p
          v-if="latest && latest.trade_date !== date"
          class="notice notice--warning"
          role="status"
        >
          所选研究日为 {{ date }}，最近已保存行情为
          {{ latest.trade_date }}。以下不是所选日收盘价，请核对缺项。
        </p>
        <div
          v-if="bars.error.value"
          class="notice notice--warning"
          role="alert"
        >
          行情读取失败：{{ bars.error.value }}
          <el-button
            link
            @click="bars.reload"
          >
            重试行情
          </el-button>
        </div>
        <SectionTabs
          v-model="activeTab"
          label="个股研究内容分类"
          :items="tabs"
        >
          <template #plans>
            <HoldingResearchPanel
              :symbol="symbol"
              :initial-horizon="initialHorizon"
              :date="date"
              :refresh-token="refreshToken"
            />
          </template>
          <template #price>
            <AsyncState
              :loading="indicators.loading.value"
              :error="indicators.error.value"
              :has-content="Boolean(indicators.data.value)"
              @retry="indicators.reload"
            >
              <p
                v-if="indicator && latest && indicator.trade_date !== latest.trade_date"
                class="notice notice--warning"
              >
                指标截至 {{ indicator.trade_date }}，与最近行情日期不同，请分别核对。
              </p>
              <div class="summary-grid">
                <article class="metric-card">
                  <span class="metric-card__label">
                    <TermHelp
                      term="ma"
                      label="MA20"
                    />
                  </span>
                  <strong>{{ number(indicator?.ma20) }}</strong>
                </article>
                <article class="metric-card">
                  <span class="metric-card__label">
                    <TermHelp
                      term="ma"
                      label="MA60"
                    />
                  </span>
                  <strong>{{ number(indicator?.ma60) }}</strong>
                </article>
                <article class="metric-card">
                  <span class="metric-card__label">
                    <TermHelp
                      term="rs20"
                      label="20日相对强度"
                    />
                  </span>
                  <strong>{{ percent(indicator?.rs20) }}</strong>
                </article>
                <article class="metric-card">
                  <span class="metric-card__label">
                    <TermHelp
                      term="volume-ratio"
                      label="成交量比"
                    />
                  </span>
                  <strong>
                    {{ number(indicator?.vol_ratio) }}
                    <small>倍</small>
                  </strong>
                </article>
              </div>
            </AsyncState>

            <SectionCard
              title="价格走势"
              description="K线与均线统一到所选日期的复权口径。"
            >
              <AsyncState
                :loading="bars.loading.value"
                :error="bars.error.value"
                :has-content="Boolean(bars.data.value)"
                @retry="bars.reload"
              >
                <PriceChart
                  v-if="bars.data.value?.items.length"
                  :bars="bars.data.value.items"
                  :indicators="indicators.data.value || []"
                />
                <el-empty
                  v-else
                  description="暂无日线，请在任务中心回填"
                />
              </AsyncState>
              <details
                v-if="indicators.data.value?.length"
                class="indicator-details"
              >
                <summary>查看最近10个交易日的完整技术指标</summary>
                <el-table :data="indicators.data.value.slice(-10)">
                  <el-table-column
                    prop="trade_date"
                    label="日期"
                    width="115"
                  />
                  <el-table-column
                    v-for="key in indicatorColumns"
                    :key="key"
                    :label="key.toUpperCase()"
                    min-width="85"
                  >
                    <template #header>
                      <TermHelp
                        :term="key.startsWith('ma') ? 'ma' : 'atr14'"
                        :label="key.toUpperCase()"
                      />
                    </template>
                    <template #default="scope">{{ number(scope.row[key]) }}</template>
                  </el-table-column>
                  <el-table-column
                    label="RS20"
                    min-width="85"
                  >
                    <template #header>
                      <TermHelp
                        term="rs20"
                        label="RS20"
                      />
                    </template>
                    <template #default="scope">{{ percent(scope.row.rs20) }}</template>
                  </el-table-column>
                  <el-table-column
                    label="量比"
                    min-width="75"
                  >
                    <template #header>
                      <TermHelp
                        term="volume-ratio"
                        label="量比"
                      />
                    </template>
                    <template #default="scope">{{ number(scope.row.vol_ratio) }}</template>
                  </el-table-column>
                  <el-table-column
                    label="数据状态"
                    min-width="110"
                  >
                    <template #default="scope">
                      <StateBadge :value="scope.row.data_status" />
                    </template>
                  </el-table-column>
                </el-table>
              </details>
            </SectionCard>
          </template>
          <template #rules>
            <SectionCard
              title="风险规则"
              description="展开每条规则查看原始证据；数据不足不等于未触发。"
            >
              <template #actions>
                <el-select
                  v-model="signalDate"
                  placeholder="最新信号日期"
                  style="width: 150px"
                >
                  <el-option
                    v-for="date in signalDates"
                    :key="date"
                    :label="date"
                    :value="date"
                  />
                </el-select>
              </template>
              <AsyncState
                :loading="signals.loading.value"
                :error="signals.error.value"
                :has-content="Boolean(signals.data.value)"
                @retry="signals.reload"
              >
                <RuleStatusGuide compact />
                <el-table
                  :data="visibleSignals"
                  empty-text="暂无风险规则记录"
                >
                  <el-table-column type="expand">
                    <template #default="s">
                      <div class="evidence-block">
                        <span class="eyebrow">{{ s.row.rule_code }}</span>
                        <pre>{{ pretty(s.row.evidence_json) }}</pre>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="trade_date"
                    label="日期"
                    width="120"
                  />
                  <el-table-column
                    label="规则"
                    min-width="180"
                  >
                    <template #default="s">
                      <TermHelp
                        :term="ruleTermIds[s.row.rule_code]"
                        :label="ruleNames[s.row.rule_code] || s.row.rule_code"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column
                    label="状态"
                    min-width="140"
                  >
                    <template #default="s"><StateBadge :value="s.row.status" /></template>
                  </el-table-column>
                  <el-table-column label="风险级别">
                    <template #header>
                      <TermHelp
                        term="risk-level"
                        label="风险级别"
                      />
                    </template>
                    <template #default="s"><StateBadge :value="s.row.severity" /></template>
                  </el-table-column>
                </el-table>
              </AsyncState>
            </SectionCard>
          </template>
          <template #financials>
            <SectionCard
              title="财报对比与经营观察"
              description="图表金额为亿元，原始表为人民币元；只读取截至所选研究日可用的披露版本。"
            >
              <AsyncState
                :loading="financials.loading.value"
                :error="financials.error.value"
                :has-content="Boolean(financials.data.value)"
                @retry="financials.reload"
              >
                <FinancialResearchPanel
                  :items="financials.data.value?.items || []"
                  :date="date"
                />
              </AsyncState>
            </SectionCard>
          </template>
        </SectionTabs>
      </template>
    </AsyncState>
  </div>
</template>
