<script setup lang="ts">
import { computed, ref } from 'vue';
import SaveResearchPlan from './SaveResearchPlan.vue';
import AsyncState from '../common/AsyncState.vue';
import SectionCard from '../common/SectionCard.vue';
import StateBadge from '../common/StateBadge.vue';
import TermHelp from '../common/TermHelp.vue';
import { useResource } from '../../composables/useResource';
import { getHoldingResearch } from '../../api/market';
import { number, sourceName } from '../../utils/format';
import { holdingStatusExplanation } from '../../content/status-explanations';
const props = defineProps<{ symbol: string; date: string; refreshToken: number }>();
const selected = ref('SHORT');
const { data, loading, error, reload } = useResource(
  (signal) => getHoldingResearch(props.symbol, props.date, signal),
  {
    watch: [() => props.symbol, () => props.date, () => props.refreshToken],
    identity: () => `${props.symbol}:${props.date}`,
  },
);
const current = computed(
  () =>
    data.value?.horizons.find((item) => item.code === selected.value) || data.value?.horizons[0],
);
const labels: Record<string, string> = {
  CONDITIONAL_MATCH: '行情条件满足 · 待复核',
  WAIT: '等待条件',
  WATCH: '优先复核减仓/退出',
  RISK_BLOCKED: '风险拦截 · 暂不新增',
  UNAVAILABLE: '证据不足 · 暂不判断',
};
</script>
<template>
  <SectionCard
    title="短、中、长期持有与买卖计划"
    description="先选持有周期，再看行动条件；价位均来自截至所选日期的已保存事实。"
  >
    <template #actions>
      <RouterLink
        to="/help"
        class="text-link"
      >
        如何使用这些计划 →
      </RouterLink>
    </template>
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <template v-if="data">
        <p class="query-note">
          研究日 {{ data.asOf }} · 最近已存行情 {{ data.quoteDate || '暂无' }} ·
          {{ data.source ? sourceName(data.source) : '本期有效行情来源未确认' }} · 本期收盘
          <strong class="reference-price">{{ number(data.close) }} 元</strong>
          。这里是当前保存版本的研究视图，历史报告保留原修订。
        </p>
        <div
          class="holding-horizon-selector"
          role="group"
          aria-label="选择持有周期"
        >
          <button
            v-for="item in data.horizons"
            :key="item.code"
            type="button"
            :aria-pressed="current?.code === item.code"
            @click="selected = item.code"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.period }}</span>
            <span>{{ labels[item.status] || item.status }}</span>
          </button>
        </div>
        <section
          v-if="current"
          class="holding-horizon"
          data-testid="holding-horizon"
        >
          <header>
            <div>
              <h3>{{ current.title }} · {{ current.period }}</h3>
              <p>{{ current.conclusion }}</p>
            </div>
            <StateBadge
              :value="current.status"
              :label="labels[current.status] || current.status"
              context="holding"
              :explanation="holdingStatusExplanation({ research: data, horizon: current })"
            />
          </header>
          <div
            v-if="current.missing.length"
            class="notice notice--warning"
          >
            <strong>本周期还缺什么</strong>
            <ul class="notice__content">
              <li
                v-for="item in current.missing"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>
          <div class="holding-actions">
            <article
              v-for="action in current.actions"
              :key="action.code"
              :data-action="action.code"
              class="holding-action"
            >
              <header>
                <h4>{{ action.title }}</h4>
                <StateBadge
                  :value="action.status"
                  :label="labels[action.status] || action.status"
                  context="holding"
                  :explanation="
                    holdingStatusExplanation({ research: data, horizon: current, action })
                  "
                />
              </header>
              <p>{{ action.conclusion }}</p>
              <dl class="holding-action__prices">
                <div v-if="action.observationZone">
                  <dt>买入 / 加仓观察带</dt>
                  <dd class="reference-price">
                    {{ number(action.observationZone.lower) }}–{{
                      number(action.observationZone.upper)
                    }}
                    元
                  </dd>
                </div>
                <div v-if="action.referencePrice != null">
                  <dt>{{ action.referenceLabel }}</dt>
                  <dd class="reference-price">{{ number(action.referencePrice) }} 元</dd>
                </div>
                <div v-if="action.stopReference != null">
                  <dt>
                    <TermHelp
                      term="stop-reference"
                      label="结构风险退出参考"
                    />
                  </dt>
                  <dd class="reference-price">{{ number(action.stopReference) }} 元</dd>
                </div>
              </dl>
              <SaveResearchPlan
                v-if="data.quoteDate === data.asOf && current.status !== 'UNAVAILABLE'"
                :symbol="symbol"
                :date="date"
                :horizon="current.code"
                :action-code="action.code"
              />
              <p class="help-text">
                {{
                  action.conditionLogic === 'ALL'
                    ? '全部列示条件须同时满足；个人前提另行确认。'
                    : '任一已确认风险组合满足，即进入减仓或退出复核。'
                }}
              </p>
              <ul class="holding-action__conditions">
                <li
                  v-for="condition in action.conditions"
                  :key="condition.label"
                >
                  <strong
                    :class="
                      condition.met === true
                        ? 'condition-met'
                        : condition.met == null
                          ? 'muted'
                          : ''
                    "
                  >
                    {{
                      condition.met === true
                        ? '已满足'
                        : condition.met === false
                          ? '未满足'
                          : '待核验'
                    }}
                    · {{ condition.label }}
                  </strong>
                  <span>实测：{{ condition.observed }}</span>
                  <span>要求：{{ condition.required }}</span>
                </li>
              </ul>
              <details class="reading-details">
                <summary>何时失效、哪些前提需要自己核对</summary>
                <ul>
                  <li
                    v-for="item in [...action.invalidation, ...action.assumptions]"
                    :key="item"
                  >
                    {{ item }}
                  </li>
                </ul>
              </details>
            </article>
          </div>
          <details class="reading-details">
            <summary>本周期证据与方法说明</summary>
            <ul>
              <li
                v-for="item in [...current.evidence, ...data.notes]"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
            <small>{{ data.version }}</small>
          </details>
          <RouterLink
            v-if="current.status === 'UNAVAILABLE'"
            to="/jobs"
            class="text-link"
          >
            到任务中心补齐所选日期研究数据 →
          </RouterLink>
        </section>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.holding-horizon-selector {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 24px;
}
.holding-horizon-selector button {
  min-width: 0;
  text-align: left;
  padding: 16px;
  border: 1px solid #dce5ed;
  border-radius: 9px;
  background: #fff;
  color: #536a80;
  cursor: pointer;
  line-height: 1.7;
}
.holding-horizon-selector button[aria-pressed='true'] {
  background: #eaf2f4;
  border-color: #235970;
}
.holding-horizon-selector strong,
.holding-horizon-selector span {
  display: block;
}
.holding-horizon-selector strong {
  color: #25344a;
  font-size: 14px;
}
.holding-horizon-selector span {
  font-size: 12px;
  margin-top: 5px;
}
.holding-horizon > header,
.holding-action > header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.holding-horizon h3 {
  font-size: 16px;
  margin: 0 0 10px;
}
.holding-horizon p,
.holding-horizon li {
  font-size: 13px;
  line-height: 1.8;
}
.holding-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 16px;
}
.holding-action {
  min-width: 0;
  border: 1px solid #e2e9ef;
  border-radius: 9px;
  padding: 18px;
}
.holding-action h4 {
  font-size: 14px;
  margin: 0;
}
.holding-action__prices {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
dt {
  font-size: 12px;
  color: #738398;
  line-height: 1.7;
}
dd {
  margin: 6px 0 0;
  font-size: 16px;
}
.holding-action__conditions {
  padding-left: 18px;
}
.holding-action__conditions li {
  margin-bottom: 14px;
  overflow-wrap: anywhere;
}
.holding-action__conditions span {
  display: block;
  color: #738398;
  font-size: 12px;
}
.condition-met {
  color: #2b8977;
}
small {
  color: #738398;
}
@media (max-width: 1100px) {
  .holding-actions {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 560px) {
  .holding-horizon-selector {
    grid-template-columns: minmax(0, 1fr);
  }
  .holding-action {
    padding: 14px;
  }
  .holding-action__prices {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
