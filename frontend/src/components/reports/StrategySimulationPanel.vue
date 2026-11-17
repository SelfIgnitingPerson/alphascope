<script setup lang="ts">
import type { StrategySimulation } from '../../types/validation';
import { percent, number } from '../../utils/format';
defineProps<{ simulation?: StrategySimulation | null }>();
const parameterNames: Record<string, string> = {
  initialCapital: '初始模拟资金（元）',
  commissionRate: '双边佣金率',
  stampDutySellRate: '卖出印花税假设',
  slippageRate: '单边滑点率',
  minimumCommission: '最低佣金（元）',
  maximumAmountParticipation: '成交额参与上限',
  lotSize: '整手股数',
  initialAllocation: '建仓及加仓资金比例',
};
const labels: Record<string, string> = {
  ALL: '全区间',
  DISCOVERY: '较早区间',
  HOLDOUT: '较晚留出',
};
</script>
<template>
  <details class="reading-details">
    <summary>
      <strong>组合策略执行模拟</strong>
      <span>次日开盘 · 费用与滑点 · 非真实成交证明</span>
    </summary>
    <p
      v-if="!simulation"
      class="help-text"
    >
      此旧验证未保存组合模拟。需显式提交新验证，原结果不会被重新计算或覆盖。
    </p>
    <template v-else>
      <p>方法 {{ simulation.version }}。各区间独立起仓，不跨留出边界沿用仓位。</p>
      <div class="simulation-grid">
        <article
          v-for="row in simulation.periods"
          :key="row.label"
        >
          <h4>{{ labels[row.label] || row.label }}</h4>
          <p>{{ row.start }} 至 {{ row.end }}</p>
          <strong>
            {{ row.status === 'INCOMPLETE' ? '证据不足，暂不报告收益' : '执行假设下的回顾结果' }}
          </strong>
          <p
            v-if="row.status !== 'INCOMPLETE' && row.completedTrades === 0"
            class="help-text"
          >
            没有完成卖出样本；空仓零收益或未平仓估值均不能证明策略有效。
          </p>
          <dl>
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
        </article>
      </div>
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
