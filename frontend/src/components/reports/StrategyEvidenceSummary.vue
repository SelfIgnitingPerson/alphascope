<script setup lang="ts">
import { computed } from 'vue';
import type { StrategySimulation } from '../../types/validation';
import { percent } from '../../utils/format';
const props = defineProps<{
  simulation?: StrategySimulation | null;
  frozenEnd: string;
  date: string;
  reproducible: boolean;
}>();
const holdout = computed(() => props.simulation?.periods.find((row) => row.label === 'HOLDOUT'));
const usable = computed(() =>
  Boolean(
    holdout.value &&
      holdout.value.status !== 'INCOMPLETE' &&
      holdout.value.completedTrades > 0 &&
      holdout.value.netReturn != null &&
      holdout.value.benchmarkReturn != null,
  ),
);
const difference = computed(() =>
  usable.value ? holdout.value!.netReturn! - holdout.value!.benchmarkReturn! : null,
);
const judgment = computed(() =>
  !usable.value
    ? '留出证据不足，暂不能评价策略优势'
    : difference.value! > 0
      ? '留出回顾跑赢基准，仍待后续验证'
      : '留出回顾未跑赢基准，尚无改善依据',
);
</script>
<template>
  <section
    class="strategy-evidence"
    data-testid="strategy-evidence-summary"
    aria-label="策略效果证据摘要"
  >
    <header>
      <h3>这套方法目前有多少依据？</h3>
      <strong>{{ judgment }}</strong>
    </header>
    <p>
      结果冻结至 {{ frozenEnd }}
      <template v-if="frozenEnd !== date">，未自动更新至所选日 {{ date }}</template>
      。
      {{ reproducible ? '冻结输入可复算；可复算不等于策略有效。' : '尚未取得完整复算通过证明。' }}
    </p>
    <dl>
      <div v-if="holdout?.tradeStatistics">
        <dt>较晚留出 · 扣费后已平仓胜率</dt>
        <dd>
          {{ percent(holdout.tradeStatistics.winRate, 2) }}
          <small>
            {{ holdout.tradeStatistics.winningTrades }} /
            {{ holdout.tradeStatistics.closedTrades }} 笔
          </small>
        </dd>
      </div>
      <div>
        <dt>较晚留出 · 扣费后净值收益</dt>
        <dd>{{ usable ? percent(holdout?.netReturn, 2) : '—' }}</dd>
      </div>
      <div>
        <dt>较晚留出 · 相对基准差额</dt>
        <dd>{{ percent(difference, 2) }}</dd>
      </div>
      <div>
        <dt>较晚留出 · 最大回撤</dt>
        <dd>{{ usable ? percent(holdout?.maximumDrawdown, 2) : '—' }}</dd>
      </div>
    </dl>
    <p v-if="holdout">
      留出区间 {{ holdout.start }} 至 {{ holdout.end }}；已完成卖出
      {{ holdout.completedTrades }} 次，跳过委托
      {{ holdout.skippedOrders }} 次。卖出次数不等于独立交易样本数。
      <template v-if="holdout.openPosition">末期仍有持仓，净值包含未实现盈亏。</template>
    </p>
    <p v-else>
      当前记录没有可用的较晚留出结果；需使用已有行情显式提交验证，不能从全区间收益推断有效。
    </p>
    <details class="reading-details">
      <summary>为什么还不能认定提高胜率？</summary>
      <ul>
        <li>这里是固定执行假设下的历史回顾；当前自选范围存在选择偏差，不能推广到所有A股。</li>
        <li>已平仓胜率按扣费后盈利的完整持仓周期统计。还需结合样本数、回撤和实际可成交性判断。</li>
        <li>
          较晚留出仍来自历史数据。还需持续记录方法冻结之后的新信号，并用后续数据检验，不能反复调参后继续称作独立验证。
        </li>
      </ul>
    </details>
  </section>
</template>
<style scoped>
.strategy-evidence {
  border: 1px solid #dce5ed;
  border-radius: 9px;
  padding: 18px;
  background: #f8fafc;
}
.strategy-evidence header {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  justify-content: space-between;
  align-items: baseline;
}
.strategy-evidence h3 {
  font-size: 15px;
  margin: 0;
}
.strategy-evidence header strong {
  font-size: 13px;
  color: #936c28;
}
.strategy-evidence p,
.strategy-evidence li {
  font-size: 12px;
  line-height: 1.9;
  color: #60758b;
}
.strategy-evidence p {
  margin: 12px 0;
}
.strategy-evidence dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.strategy-evidence dt {
  font-size: 12px;
  color: #738398;
}
.strategy-evidence dd {
  margin: 8px 0;
  font-size: 20px;
  font-weight: 600;
}
@media (max-width: 700px) {
  .strategy-evidence dl {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
