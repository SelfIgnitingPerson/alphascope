<script setup lang="ts">
import { computed, ref } from 'vue';
import type { StrategySimulation } from '../../types/validation';
import { percent, number } from '../../utils/format';
import ReplayTradeDetails from './ReplayTradeDetails.vue';
import StockMediumReplay from './StockMediumReplay.vue';
const props = defineProps<{ simulation?: StrategySimulation | null; shared?: boolean }>();
const capital = computed(() => props.simulation?.parameters.initialCapital ?? 0);
const parameterNames: Record<string, string> = {
  initialCapital: '初始模拟资金（元）',
  commissionRate: '双边佣金率',
  stampDutySellRate: '卖出印花税假设',
  slippageRate: '单边滑点率',
  minimumCommission: '最低佣金（元）',
  maximumAmountParticipation: '成交额参与上限',
  lotSize: '整手股数',
  initialAllocation: '建仓及加仓资金比例',
  maximumPositionWeight: '单股仓位上限',
  perPositionRisk: '单股至止损风险上限',
};
const labels: Record<string, string> = {
  ALL: '全区间',
  DISCOVERY: '较早区间',
  HOLDOUT: '较晚留出',
};
const regimeLabels: Record<string, string> = {
  UP: '上涨',
  DOWN: '下跌',
  SIDEWAYS: '震荡',
  UNKNOWN: '基准不足',
};
const comparisonPeriod = ref('ALL');
const detailRule = ref('ORIGINAL');
const holdPeriods = computed(
  () => props.simulation?.comparisons?.find(({ code }) => code === 'BUY_AND_HOLD')?.periods,
);
const periodRows = computed(
  () =>
    props.simulation?.periods.map((row) => {
      const hold = holdPeriods.value?.find(({ label }) => label === row.label);
      const excess =
        row.netReturn != null && hold?.netReturn != null
          ? capital.value * (row.netReturn - hold.netReturn)
          : null;
      return { ...row, hold, excess };
    }) ?? [],
);
const comparisonRows = computed(() => {
  const simulation = props.simulation;
  if (!simulation?.comparisons?.length) return [];
  const groups = [
    { code: 'ORIGINAL', title: '原量价规则', periods: simulation.periods },
    ...simulation.comparisons,
  ];
  return groups.flatMap((group) => {
    const row = group.periods.find((period) => period.label === comparisonPeriod.value);
    return row ? [{ ...row, code: group.code, title: group.title }] : [];
  });
});
const volumePair = computed(() => ({
  original: comparisonRows.value.find(({ code }) => code === 'ORIGINAL'),
  volume: comparisonRows.value.find(({ code }) => code === 'ORIGINAL_VOLUME'),
}));
const tradeDetails = computed(
  () => comparisonRows.value.find(({ code }) => code === detailRule.value)?.details,
);
</script>
<template>
  <details
    class="reading-details"
    :open="shared"
  >
    <summary>
      <strong>{{ shared ? '共用资金回放' : '独立资金执行模拟' }}</strong>
      <span>模拟开盘成交 · 费用与滑点</span>
    </summary>
    <p
      v-if="!simulation"
      class="help-text"
    >
      此旧验证未保存组合模拟。需显式提交新验证，原结果不会被重新计算或覆盖。
    </p>
    <template v-else>
      <p v-if="simulation.parameters.perPositionRisk != null">
        本金 {{ number(capital) }} 元 · 单股仓位上限
        {{ percent(simulation.parameters.maximumPositionWeight) }} · 单股至止损风险上限
        {{ percent(simulation.parameters.perPositionRisk) }}
      </p>
      <p>方法 {{ simulation.version }}。各区间独立起仓，不跨留出边界沿用仓位。</p>
      <div class="simulation-grid">
        <article
          v-for="row in periodRows"
          :key="row.label"
        >
          <h4>{{ labels[row.label] || row.label }}</h4>
          <p>{{ row.start }} 至 {{ row.end }}</p>
          <strong>
            {{ row.status === 'INCOMPLETE' ? '证据不足，暂不报告收益' : '执行假设下的回顾结果' }}
          </strong>
          <dl
            v-if="row.hold"
            class="hold-comparison"
          >
            <div>
              <dt>原规则比同股持有多赚／少赚</dt>
              <dd>
                {{
                  row.excess == null
                    ? '暂无法对照'
                    : `${row.excess >= 0 ? '+' : ''}${number(row.excess)} 元`
                }}
              </dd>
            </div>
            <div>
              <dt>同股持有净盈亏／回撤</dt>
              <dd v-if="row.hold.netReturn != null">
                {{ number(capital * row.hold.netReturn) }} 元 ／
                {{ percent(row.hold.maximumDrawdown, 2) }}
              </dd>
              <dd v-else>—</dd>
            </div>
          </dl>
          <p
            v-if="row.hold"
            class="help-text"
          >
            {{ row.hold.reason }}
          </p>
          <p
            v-if="row.status !== 'INCOMPLETE' && row.completedTrades === 0"
            class="help-text"
          >
            没有完成卖出样本；空仓零收益或未平仓估值均不能证明策略有效。
          </p>
          <dl>
            <div v-if="shared && row.netReturn != null">
              <dt>期末资金／扣费后盈亏</dt>
              <dd>
                {{ number(capital * (1 + row.netReturn)) }} /
                {{ number(capital * row.netReturn) }} 元
              </dd>
            </div>
            <div v-if="row.tradeStatistics">
              <dt>扣费后已平仓胜率</dt>
              <dd>
                <template v-if="row.tradeStatistics.closedTrades">
                  {{ percent(row.tradeStatistics.winRate, 2) }}
                  （{{ row.tradeStatistics.winningTrades }} /
                  {{ row.tradeStatistics.closedTrades }}）
                </template>
                <template v-else>没有完整平仓</template>
              </dd>
            </div>
            <div v-if="row.details && row.tradeQuality?.closedPnlWithoutBestWinner != null">
              <dt>去掉最赚一笔后的平仓盈亏</dt>
              <dd>{{ number(row.tradeQuality.closedPnlWithoutBestWinner) }} 元</dd>
            </div>
            <div>
              <dt>扣费后净值收益</dt>
              <dd>{{ percent(row.netReturn, 2) }}</dd>
            </div>
            <div>
              <dt>最大回撤</dt>
              <dd>{{ percent(row.maximumDrawdown, 2) }}</dd>
            </div>
            <div>
              <dt>基准收益</dt>
              <dd>{{ percent(row.benchmarkReturn, 2) }}</dd>
            </div>
            <div>
              <dt>已卖出次数／跳过委托</dt>
              <dd>{{ row.completedTrades }} / {{ row.skippedOrders }}</dd>
            </div>
            <div v-if="row.tradeQuality">
              <dt>退出延迟最长</dt>
              <dd>
                {{ row.tradeQuality.longestExitDelay }} 日{{
                  row.tradeQuality.pendingExit ? ' · 仍有待退出' : ''
                }}
              </dd>
            </div>
            <div>
              <dt>模拟费用</dt>
              <dd>{{ number(row.fees) }} 元</dd>
            </div>
            <div>
              <dt>末期持仓</dt>
              <dd>{{ row.openPosition ? '仍有持仓，未假定卖出' : '无' }}</dd>
            </div>
          </dl>
          <p class="help-text">{{ row.reason }}</p>
          <details v-if="row.skipReasons && Object.keys(row.skipReasons).length">
            <summary>查看未成交原因</summary>
            <ul>
              <li
                v-for="(count, reason) in row.skipReasons"
                :key="reason"
              >
                {{ reason }} · {{ count }} 次
              </li>
            </ul>
          </details>
        </article>
      </div>
      <StockMediumReplay
        v-if="simulation.stockMediumReplays?.length"
        :groups="simulation.stockMediumReplays"
        :capital="capital"
      />
      <details v-if="simulation.comparisons?.length">
        <summary>规则对照与逐笔结果</summary>
        <p class="help-text">
          各组使用同样的证券、资金和成本。已平仓盈亏按扣费后计算，区间收益含末期持仓估值。
        </p>
        <el-select
          v-model="comparisonPeriod"
          aria-label="对照区间"
        >
          <el-option
            v-for="(title, code) in labels"
            :key="code"
            :label="title"
            :value="code"
          />
        </el-select>
        <p
          v-if="volumePair.volume"
          class="help-text"
        >
          5日成交额确认组只在建仓时增加一项条件。最近5日平均成交额须达到此前20日均值，加仓和卖出沿用原规则。
          本区间拦下 {{ volumePair.volume.details?.filteredEntries ?? '—' }} 次建仓信号。
        </p>
        <el-table
          :data="comparisonRows"
          row-key="code"
        >
          <el-table-column
            prop="title"
            label="规则"
            min-width="225"
          />
          <el-table-column
            label="净收益"
            min-width="92"
          >
            <template #default="{ row }">{{ percent(row.netReturn, 2) }}</template>
          </el-table-column>
          <el-table-column
            label="净盈亏（元）"
            min-width="120"
          >
            <template #default="{ row }">
              {{ row.netReturn == null ? '—' : number(capital * row.netReturn) }}
            </template>
          </el-table-column>
          <el-table-column
            label="回撤"
            min-width="92"
          >
            <template #default="{ row }">{{ percent(row.maximumDrawdown, 2) }}</template>
          </el-table-column>
          <el-table-column
            label="盈利／平仓"
            min-width="105"
          >
            <template #default="{ row }">
              {{
                row.tradeStatistics
                  ? `${row.tradeStatistics.winningTrades}／${row.tradeStatistics.closedTrades}`
                  : '—'
              }}
            </template>
          </el-table-column>
          <el-table-column
            label="平仓均值（元）"
            min-width="134"
          >
            <template #default="{ row }">{{ number(row.tradeQuality?.averageClosedPnl) }}</template>
          </el-table-column>
          <el-table-column
            label="剔除最大盈利后（元）"
            min-width="160"
          >
            <template #default="{ row }">
              {{ number(row.tradeQuality?.closedPnlWithoutBestWinner) }}
            </template>
          </el-table-column>
          <el-table-column
            label="入场市场状态"
            min-width="235"
          >
            <template #default="{ row }">
              <ul
                v-if="
                  row.tradeQuality?.marketRegimes?.some(
                    (regime: { closedTrades: number }) => regime.closedTrades,
                  )
                "
                class="regime-list"
              >
                <li
                  v-for="regime in row.tradeQuality.marketRegimes.filter(
                    (regime: { closedTrades: number }) => regime.closedTrades,
                  )"
                  :key="regime.code"
                >
                  {{ regimeLabels[regime.code] || regime.code }} {{ regime.winningTrades }}／{{
                    regime.closedTrades
                  }}
                  · {{ number(regime.netPnl) }} 元
                </li>
              </ul>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column
            label="证据"
            min-width="160"
          >
            <template #default="{ row }">
              {{
                row.status === 'INCOMPLETE' || row.code === 'BUY_AND_HOLD'
                  ? row.reason
                  : '历史回放候选'
              }}
            </template>
          </el-table-column>
        </el-table>
        <section
          v-if="volumePair.original?.details"
          class="trade-details"
        >
          <h4>逐笔盈亏与执行过程</h4>
          <el-radio-group
            v-model="detailRule"
            aria-label="逐笔记录规则"
          >
            <el-radio-button value="ORIGINAL">原量价规则</el-radio-button>
            <el-radio-button
              v-if="volumePair.volume"
              value="ORIGINAL_VOLUME"
            >
              加5日成交额确认
            </el-radio-button>
          </el-radio-group>
          <ReplayTradeDetails
            v-if="tradeDetails"
            :key="`${detailRule}-${comparisonPeriod}`"
            :details="tradeDetails"
          />
          <p
            v-else
            class="help-text"
          >
            当前区间证据不足，无法显示逐笔明细。
          </p>
        </section>
      </details>
      <ul class="help-text">
        <li
          v-for="item in simulation.limitations"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
      <details>
        <summary>查看冻结执行参数</summary>
        <dl>
          <div
            v-for="(value, key) in simulation.parameters"
            :key="key"
          >
            <dt>{{ parameterNames[key] || key }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </details>
    </template>
  </details>
</template>
<style scoped>
.hold-comparison {
  padding: 10px;
  border-radius: 6px;
  background: #eef5f6;
}
.hold-comparison dd {
  font-weight: 600;
}
.trade-details {
  margin-top: 20px;
}
.regime-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.simulation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.simulation-grid article {
  padding: 14px;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  min-width: 0;
}
.simulation-grid h4 {
  margin-top: 0;
}
.simulation-grid p,
.simulation-grid dl {
  font-size: 12px;
  line-height: 1.8;
}
.simulation-grid dl > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.simulation-grid dd {
  margin: 0;
}
@media (max-width: 1000px) {
  .simulation-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
