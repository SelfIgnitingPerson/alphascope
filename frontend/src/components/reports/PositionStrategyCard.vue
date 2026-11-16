<script setup lang="ts">
import type { PositionStrategy } from '../../types/position-strategy';
import type { PriceVolumeZone } from '../../types/price-volume';
import { number, percent } from '../../utils/format';
import TermHelp from '../common/TermHelp.vue';
import StateBadge from '../common/StateBadge.vue';
defineProps<{ strategies: PositionStrategy[] }>();
const states: Record<string, string> = {
  CONDITIONAL_MATCH: '行情条件满足 · 待人工复核',
  WAIT: '等待条件',
  WATCH: '复核减仓条件',
  RISK_BLOCKED: '风险过滤 · 暂不执行',
  UNAVAILABLE: '资料不足',
};
const zone = (item: PriceVolumeZone) => `${number(item.lower)}—${number(item.upper)} 元`;
const matched = (plan: PositionStrategy) =>
  plan.conditions.filter((item) => item.met === true).length;
const pending = (plan: PositionStrategy) => plan.conditions.filter((item) => item.met !== true);
const unknown = (plan: PositionStrategy) =>
  plan.conditions.filter((item) => item.met == null).length;
const conditionLabel = (met: boolean | null) =>
  met === true ? '已满足' : met === false ? '未满足' : '待核验';
</script>
<template>
  <section class="position-strategies">
    <h4>
      <TermHelp
        term="entry-band"
        label="建仓、加仓与减仓策略"
      />
    </h4>
    <p class="help-text">各方案独立判断；减仓条件优先于新建或加仓，不因“未触发减仓”就建议买入。</p>
    <article
      v-for="plan in strategies"
      :key="plan.code"
      class="position-plan"
      :data-strategy="plan.code"
    >
      <header>
        <h5>{{ plan.title }}</h5>
        <StateBadge
          :value="plan.status"
          :label="states[plan.status] || plan.status"
          context="holding"
          :explanation="{
            subject: plan.title + ' · 报告冻结方案',
            reason: plan.conclusion,
            missing: plan.conditions
              .filter((item) => item.met == null)
              .map((item) => `${item.label}：${item.observed}；要求${item.required}`),
            facts: plan.conditions
              .filter((item) => item.met === false)
              .map((item) => `未满足：${item.label}；实测${item.observed}；要求${item.required}`),
            action: '按本报告冻结时的证据核对组合条件与个人前提；当前计划需到自选股详情查看。',
          }"
        />
      </header>
      <p>{{ plan.conclusion }}</p>
      <dl
        class="position-plan__prices"
        v-if="plan.observationZone || plan.referenceZone || plan.stopReference != null"
      >
        <div v-if="plan.observationZone">
          <dt>{{ plan.code === 'ADD_RETEST' ? '加仓观察带' : '建仓观察带' }}</dt>
          <dd>
            <strong class="reference-price">{{ zone(plan.observationZone) }}</strong>
          </dd>
        </div>
        <div v-if="plan.referenceZone">
          <dt>
            <TermHelp
              term="support"
              label="原结构参考"
            />
          </dt>
          <dd>
            <strong class="reference-price">{{ zone(plan.referenceZone) }}</strong>
          </dd>
        </div>
        <div v-if="plan.stopReference != null">
          <dt>
            <TermHelp
              term="stop-reference"
              label="结构止损参考"
            />
          </dt>
          <dd>
            <strong class="reference-price">{{ number(plan.stopReference) }} 元</strong>
          </dd>
        </div>
        <div v-if="plan.referenceRisk != null">
          <dt>
            <TermHelp
              term="risk-distance"
              label="观察带上沿至止损距离"
            />
          </dt>
          <dd>
            {{ percent(plan.referenceRisk, 2) }} / 技术上限
            {{ percent(plan.maximumReferenceRisk, 0) }}
          </dd>
        </div>
      </dl>
      <p
        v-if="plan.conditionLogic === 'ANY'"
        class="help-text"
      >
        已确认触发 {{ matched(plan) }} 类风险组合。
        <template v-if="unknown(plan)">
          另有 {{ unknown(plan) }} 类待核验，未知不代表未触发。
        </template>
        <template v-else>本期已核验 {{ plan.conditions.length }} 类。</template>
        任一满足即进入减仓复核。
      </p>
      <div
        v-else-if="plan.status !== 'CONDITIONAL_MATCH' && pending(plan).length"
        class="position-plan__pending"
      >
        <strong>尚待满足或核验</strong>
        <ul>
          <li
            v-for="item in pending(plan).slice(0, 3)"
            :key="item.label"
          >
            {{ item.label }}：{{ item.required }}
          </li>
        </ul>
        <span
          v-if="pending(plan).length > 3"
          class="help-text"
        >
          另有 {{ pending(plan).length - 3 }} 项，见全部条件。
        </span>
      </div>
      <details>
        <summary>
          全部行情条件与依据 · 已满足 {{ matched(plan) }}/{{ plan.conditions.length }} 项
        </summary>
        <ul class="position-plan__conditions">
          <li
            v-for="item in plan.conditions"
            :key="item.label"
          >
            <strong>{{ conditionLabel(item.met) }} · {{ item.label }}</strong>
            <span>实测：{{ item.observed }}</span>
            <span>要求：{{ item.required }}</span>
          </li>
        </ul>
      </details>
      <details>
        <summary>失效条件与个人适用前提</summary>
        <ul>
          <li
            v-for="item in plan.invalidation"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
        <ul>
          <li
            v-for="item in plan.assumptions"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
        <small>{{ plan.version }}</small>
      </details>
    </article>
  </section>
</template>
<style scoped>
.position-strategies {
  margin: 24px 0;
}
h4 {
  margin: 0 0 10px;
  font-size: 16px;
}
h5 {
  margin: 0;
  font-size: 15px;
}
.position-plan {
  border: 1px solid #dce5ed;
  border-radius: 10px;
  padding: 18px;
  margin: 14px 0;
  background: #fff;
  font-size: 13px;
  line-height: 1.8;
}
.position-plan header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.position-plan__prices {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
dt {
  color: #64748b;
  font-size: 12px;
}
dd {
  margin: 4px 0 0;
  font-variant-numeric: tabular-nums;
}
ul {
  padding-left: 18px;
}
.position-plan__pending {
  background: #f8fafc;
  padding: 10px 14px;
  margin: 14px 0;
  border-radius: 8px;
}
.position-plan__pending ul {
  margin: 4px 0;
}
details {
  margin-top: 12px;
}
summary {
  cursor: pointer;
  color: #475569;
}
.position-plan__conditions li {
  margin-bottom: 12px;
}
.position-plan__conditions span {
  display: block;
  color: #64748b;
  overflow-wrap: anywhere;
}
small {
  color: #64748b;
}
@media (max-width: 700px) {
  .position-plan__prices {
    grid-template-columns: 1fr;
  }
  .position-plan {
    padding: 12px;
  }
}
</style>
