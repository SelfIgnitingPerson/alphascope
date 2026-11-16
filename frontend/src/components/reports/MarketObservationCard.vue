<script setup lang="ts">
import TermHelp from '../common/TermHelp.vue';
import type { MarketObservation } from '../../types/models';
import StateBadge from '../common/StateBadge.vue';
import { number, percent } from '../../utils/format';
defineProps<{ observation: MarketObservation }>();
const amount = (v: number | null) => (v == null ? '—' : number(v / 1e8) + ' 亿元');
</script>
<template>
  <section class="market-observation">
    <p
      v-for="note in observation.limitations.filter((item) => item.includes('10001011'))"
      :key="note"
      class="notice notice--subtle"
    >
      {{ note }}
    </p>
    <header>
      <div>
        <h3>沪深市场分项观察</h3>
        <p class="help-text">
          截至 {{ observation.businessDate }} · 沪深A股，不含北交所 · 独立观察，不合成全A总分
        </p>
      </div>
      <StateBadge
        :value="observation.status"
        :label="observation.status === 'AVAILABLE' ? '三项可观察' : undefined"
      />
    </header>
    <div class="observation-parts">
      <article>
        <h4>指数趋势</h4>
        <strong>{{ observation.trend.judgment }}</strong>
        <dl>
          <div>
            <dt>
              <TermHelp
                term="benchmark"
                label="沪深300收盘"
              />
            </dt>
            <dd>{{ number(observation.trend.close) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="ma"
                label="MA20 / MA60"
              />
            </dt>
            <dd>{{ number(observation.trend.ma20) }} / {{ number(observation.trend.ma60) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="ma"
                label="MA20较前日变化"
              />
            </dt>
            <dd>{{ number(observation.trend.ma20Slope) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="return"
                label="指数当日收益"
              />
            </dt>
            <dd>{{ percent(observation.trend.dayReturn, 2) }}</dd>
          </div>
        </dl>
        <p class="help-text">{{ observation.trend.explanation }}</p>
      </article>
      <article>
        <h4>成交额与方向</h4>
        <strong>{{ observation.turnover.judgment }}</strong>
        <dl>
          <div>
            <dt>
              <TermHelp
                term="volume"
                label="当日成交额"
              />
            </dt>
            <dd>{{ amount(observation.turnover.amount) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="snapshot"
                label="5日 / 20日均值"
              />
            </dt>
            <dd>
              {{ amount(observation.turnover.average5) }} /
              {{ amount(observation.turnover.average20) }}
            </dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="snapshot"
                label="相对5日 / 20日均值"
              />
            </dt>
            <dd>
              {{ number(observation.turnover.ratio5) }} /
              {{ number(observation.turnover.ratio20) }} 倍
            </dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="snapshot"
                label="连续完整截面"
              />
            </dt>
            <dd>{{ observation.turnover.completeDays }}/20 天</dd>
          </div>
        </dl>
        <p class="help-text">{{ observation.turnover.explanation }}</p>
      </article>
      <article>
        <h4>市场均线广度</h4>
        <strong>{{ observation.breadth.judgment }}</strong>
        <dl>
          <div>
            <dt>
              <TermHelp
                term="breadth"
                label="站上MA20 / MA60"
              />
            </dt>
            <dd>
              {{ percent(observation.breadth.ratio20, 2) }} /
              {{ percent(observation.breadth.ratio60, 2) }}
            </dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="coverage"
                label="MA20有效 / 当日交易"
              />
            </dt>
            <dd>
              {{ observation.breadth.valid20 }} / {{ observation.breadth.trading }} · 覆盖
              {{ percent(observation.breadth.coverage20, 2) }}
            </dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="coverage"
                label="MA60有效 / 当日交易"
              />
            </dt>
            <dd>
              {{ observation.breadth.valid60 }} / {{ observation.breadth.trading }} · 覆盖
              {{ percent(observation.breadth.coverage60, 2) }}
            </dd>
          </div>
          <div>
            <dt>上涨占比 / 停牌家数</dt>
            <dd>
              {{ percent(observation.breadth.advancingRatio, 2) }} /
              {{ observation.breadth.suspended }}
            </dd>
          </div>
        </dl>
        <p class="help-text">{{ observation.breadth.explanation }}</p>
      </article>
    </div>
    <details>
      <summary>计算口径、阈值与覆盖边界</summary>
      <ul>
        <li
          v-for="limit in observation.limitations"
          :key="limit"
        >
          {{ limit }}
        </li>
      </ul>
      <p class="help-text">
        量能扩张线 {{ number(observation.parameters.turnoverExpansion) }} 倍、收缩线
        {{ number(observation.parameters.turnoverContraction) }} 倍；广度偏强/偏弱观察线
        {{ percent(observation.parameters.breadthStrong, 0) }}/{{
          percent(observation.parameters.breadthWeak, 0)
        }}；均线最低覆盖 {{ percent(observation.parameters.minimumCoverage, 0) }}。
      </p>
    </details>
  </section>
</template>
<style scoped>
.market-observation {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 24px;
  min-width: 0;
  background: #fff;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
h3 {
  margin: 0;
}
h4 {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}
article > strong {
  font-size: 18px;
  line-height: 1.6;
}
.observation-parts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin: 20px 0;
}
article {
  min-width: 0;
  padding: 16px;
  border-radius: 10px;
  background: #f8fafc;
}
dl {
  margin: 16px 0;
}
dl > div {
  margin: 12px 0;
}
dt {
  color: #64748b;
  font-size: 12px;
}
dd {
  margin: 5px 0 0;
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}
details {
  font-size: 13px;
  line-height: 1.9;
}
summary {
  cursor: pointer;
}
@media (max-width: 1000px) {
  .observation-parts {
    grid-template-columns: minmax(0, 1fr);
  }
  .market-observation {
    padding: 16px;
  }
}
</style>
