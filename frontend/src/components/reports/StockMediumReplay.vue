<script setup lang="ts">
import { computed, ref } from 'vue';
import type { StrategySimulation } from '../../types/validation';
import { number, percent } from '../../utils/format';
import ReplayTradeDetails from './ReplayTradeDetails.vue';

const props = defineProps<{
  groups: NonNullable<StrategySimulation['stockMediumReplays']>;
  capital: number;
}>();
const selectedSymbol = ref('');
const interval = ref('ALL');
const labels: Record<string, string> = {
  ALL: '全区间',
  DISCOVERY: '较早区间',
  HOLDOUT: '较晚区间',
};
const selected = computed(
  () => props.groups.find(({ symbol }) => symbol === selectedSymbol.value) ?? props.groups[0],
);
const options = computed(() =>
  (selected.value?.trend ?? []).map(({ label }) => ({ label, name: labels[label] ?? label })),
);
const rows = computed(() => {
  if (!selected.value) return [];
  const choices = [
    { name: '原中期回踩', periods: selected.value.original },
    { name: '中期顺趋势候选', periods: selected.value.trend },
  ];
  return choices.flatMap(({ name, periods }) => {
    const period = periods.find(({ label }) => label === interval.value) ?? periods[0];
    return period ? [{ name, period, entry: period.details?.entryExecution }] : [];
  });
});
</script>

<template>
  <details
    class="reading-details"
    data-testid="stock-medium-replay"
  >
    <summary>中期逐股独立回放</summary>
    <p class="help-text">
      每只股票、每个方案各用 {{ number(capital, 0) }} 元从空仓起步。顺趋势方案按前后两日收盘和 60
      日均线判断买点，再按次日开盘核对资金和风险预算。
    </p>
    <div class="medium-filters">
      <el-select
        v-model="selectedSymbol"
        :placeholder="selected?.symbol"
        aria-label="中期回放证券"
      >
        <el-option
          v-for="stock in groups"
          :key="stock.symbol"
          :label="stock.symbol"
          :value="stock.symbol"
        />
      </el-select>
      <el-select
        v-model="interval"
        aria-label="中期回放区间"
      >
        <el-option
          v-for="item in options"
          :key="item.label"
          :label="item.name"
          :value="item.label"
        />
      </el-select>
    </div>
    <div class="medium-rows">
      <article
        v-for="row in rows"
        :key="row.name"
      >
        <h4>{{ row.name }}</h4>
        <template v-if="row.entry">
          <p>
            <strong>
              建仓信号 {{ row.entry.signals }} 次 · 模拟建仓成交 {{ row.entry.filled }} 次
            </strong>
          </p>
          <ul v-if="Object.keys(row.entry.blocked).length">
            <li
              v-for="(occurrences, reason) in row.entry.blocked"
              :key="reason"
            >
              {{ reason }} · {{ occurrences }} 次
            </li>
          </ul>
          <p>
            扣费后盈亏
            {{ number(row.period.netReturn == null ? null : row.period.netReturn * capital) }} 元 ·
            最大回撤 {{ percent(row.period.maximumDrawdown, 2) }}
          </p>
          <p v-if="row.period.tradeStatistics?.closedTrades">
            盈利 {{ row.period.tradeStatistics.winningTrades }}／{{
              row.period.tradeStatistics.closedTrades
            }}
            个完整平仓周期
            <span v-if="row.period.openPosition">· 期末仍有持仓</span>
          </p>
          <p
            v-else
            class="help-text"
          >
            {{ row.period.openPosition ? '期末仍有持仓，暂无完整平仓。' : '暂无完整平仓样本。' }}
          </p>
        </template>
        <p
          v-else
          class="help-text"
        >
          {{ row.period.reason }}
        </p>
      </article>
    </div>
    <details
      v-for="row in rows.filter(({ period }) => period.details)"
      :key="row.name"
    >
      <summary>{{ row.name }} · 买点、模拟成交与一手预算</summary>
      <ReplayTradeDetails
        v-if="row.period.details"
        :key="selected?.symbol + interval"
        :details="row.period.details"
      />
    </details>
  </details>
</template>

<style scoped>
.medium-filters,
.medium-rows {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 265px), 1fr));
  gap: 12px;
  margin-block: 12px;
}
.medium-rows article {
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  padding: 12px;
}
.medium-rows h4 {
  margin-top: 0;
}
</style>
