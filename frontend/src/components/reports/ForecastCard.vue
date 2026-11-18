<script setup lang="ts">
import ObservationSummary from '../common/ObservationSummary.vue';
import { forecastObservation } from '../../utils/observation-summaries';
import TermHelp from '../common/TermHelp.vue';
import type { ForecastView } from '../../types/models';
import { number, timestamp } from '../../utils/format';
defineProps<{ forecast: ForecastView }>();
</script>
<template>
  <article class="forecast-card">
    <h3>
      {{ forecast.name }}
      <small>{{ forecast.symbol }}</small>
    </h3>
    <ObservationSummary
      :priority="forecastObservation(forecast)"
      context="盈利预期先关注什么"
    />
    <details class="reading-details">
      <summary>展开预测数值、历史修订与来源</summary>
      <template v-if="forecast.latest">
        <p class="help-text">
          首次观测于 {{ timestamp(forecast.latest.availableAt) }} · 近六个月评级机构
          {{ forecast.latest.ratingAgencies }} 家
        </p>
        <div class="forecast-estimates">
          <div
            v-for="item in forecast.latest.estimates"
            :key="item.year"
          >
            <span>
              <TermHelp
                term="eps"
                :label="`${item.year}年预测EPS`"
              />
            </span>
            <strong>
              {{ number(item.eps, 3) }}
              <small>元/股</small>
            </strong>
          </div>
        </div>
        <p>{{ forecast.explanation }}</p>
        <ul v-if="forecast.changes.length">
          <li
            v-for="change in forecast.changes"
            :key="change.year"
          >
            {{ change.year }}年：{{ number(change.previousEps, 3) }} →
            {{ number(change.currentEps, 3) }} 元/股，差额
            {{ number(change.difference, 3) }}；对比观测
            {{ timestamp(change.previousObservedAt) }} →
            {{ timestamp(change.currentObservedAt) }}
          </li>
        </ul>
        <p
          v-if="forecast.collection.status === 'UNAVAILABLE'"
          class="help-text"
        >
          最近一次采集未成功，当前展示已保存快照，请核对观测时间。
        </p>
        <a
          :href="forecast.latest.sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          查看供应商盈利预测来源 ↗
        </a>
      </template>
      <p v-else>{{ forecast.explanation }}</p>
    </details>
    <details>
      <summary>预测口径与使用边界</summary>
      <ul>
        <li
          v-for="limit in forecast.limitations"
          :key="limit"
        >
          {{ limit }}
        </li>
      </ul>
    </details>
  </article>
</template>
<style scoped>
.forecast-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 24px;
  min-width: 0;
  background: #fff;
}
.forecast-card h3 {
  margin: 0 0 12px;
}
.forecast-card h3 small {
  color: #64748b;
  font-size: 13px;
}
.forecast-estimates {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}
.forecast-estimates span {
  display: block;
  color: #64748b;
  font-size: 12px;
}
.forecast-estimates strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
  overflow-wrap: anywhere;
}
.forecast-estimates small {
  font-size: 12px;
  font-weight: 400;
}
p,
li,
details,
a {
  font-size: 13px;
  line-height: 1.9;
}
details {
  margin-top: 16px;
}
summary {
  cursor: pointer;
}
@media (max-width: 600px) {
  .forecast-card {
    padding: 16px;
  }
  .forecast-estimates {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
