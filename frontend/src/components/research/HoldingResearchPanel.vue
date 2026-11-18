<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import SaveResearchPlan from './SaveResearchPlan.vue';
import RiskReferenceGuide from './RiskReferenceGuide.vue';
import ObservationSummary from '../common/ObservationSummary.vue';
import { researchMethodLabel } from '../../content/plan-labels';
import AsyncState from '../common/AsyncState.vue';
import SectionCard from '../common/SectionCard.vue';
import StateBadge from '../common/StateBadge.vue';
import TermHelp from '../common/TermHelp.vue';
import { useResource } from '../../composables/useResource';
import { getHoldingResearch } from '../../api/market';
import { number, sourceName } from '../../utils/format';
import type { HoldingAction } from '../../types/holding-research';
import { holdingStatusExplanation } from '../../content/status-explanations';
const props = defineProps<{
  symbol: string;
  date: string;
  refreshToken: number;
  initialHorizon?: string;
}>();
const selected = ref(props.initialHorizon || 'SHORT');
watch(
  () => props.initialHorizon,
  (value) => {
    selected.value = value || 'SHORT';
  },
);
const selectedAction = ref('');
watch(
  () => [props.symbol, props.date, selected.value],
  () => {
    selectedAction.value = '';
  },
);
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
/** 首次打开优先展示已触发的风险方案；用户可以主动切换到其他方案。 */
const action = computed(() => {
  const actions = current.value?.actions || [];
  return (
    actions.find((item) => item.code === selectedAction.value) ||
    actions.find(
      (item) =>
        item.conditionLogic === 'ANY' && ['CONDITIONAL_MATCH', 'WATCH'].includes(item.status),
    ) ||
    actions[0]
  );
});
function conditionProgress(item: HoldingAction) {
  const confirmed = item.conditions.filter((condition) => condition.met === true).length;
  return item.conditions.length
    ? `${confirmed}/${item.conditions.length}项条件满足`
    : '暂无可核验条件';
}
const labels: Record<string, string> = {
  CONDITIONAL_MATCH: '行情条件满足 · 待复核',
  WAIT: '等待条件',
  WATCH: '优先复核减仓/退出',
  RISK_BLOCKED: '暂不建仓或加仓',
  UNAVAILABLE: '证据不足 · 暂不判断',
};
/** 先分别回答新建仓和已有持仓的动作，再展示计算依据。 */
function actionLabel(action: HoldingAction) {
  if (action.conditionLogic === 'ANY' && action.status === 'CONDITIONAL_MATCH')
    return '减仓 / 退出条件已触发';
  if (action.status === 'CONDITIONAL_MATCH')
    return action.code.includes('ADD') ? '加仓条件满足 · 待复核' : '建仓条件满足 · 待复核';
  return labels[action.status] || action.status;
}
function entryDecision(marker: 'OPEN' | 'ADD') {
  const actions = current.value?.actions.filter((action) => action.code.includes(marker)) || [];
  const verb = marker === 'OPEN' ? '建仓' : '加仓';
  if (!actions.length || actions.every((action) => action.status === 'UNAVAILABLE'))
    return `资料不足，暂不能判断是否${verb}`;
  const risk = current.value?.actions.some(
    (action) =>
      action.conditionLogic === 'ANY' && ['CONDITIONAL_MATCH', 'WATCH'].includes(action.status),
  );
  if (risk)
    return marker === 'OPEN' ? '暂不建仓，已有减仓或退出复核信号' : '先复核减仓或退出，暂不加仓';
  if (actions.some((action) => action.status === 'CONDITIONAL_MATCH'))
    return `${verb}行情条件满足，仍需核对方案前提`;
  if (actions.some((action) => action.status === 'RISK_BLOCKED'))
    return `暂不${verb}，未通过风险过滤`;
  return `等待${verb}条件，目前未形成确认`;
}
</script>
<template>
  <SectionCard
    title="短、中、长期持有与买卖计划"
    description="先看当前判断，再选择要跟踪的方案；保存后可在研究计划中复核变化。"
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
          。
        </p>
        <ObservationSummary
          v-if="data.priorities?.length"
          :priority="data.priorities[0]"
          context="这只股票先关注什么"
        />
        <details
          v-if="(data.priorities?.length || 0) > 1"
          class="reading-details"
          data-testid="other-horizon-priorities"
        >
          <summary>其他周期的风险与观察线索（{{ data.priorities!.length - 1 }}项）</summary>
          <ul>
            <li
              v-for="item in data.priorities!.slice(1)"
              :key="`${item.horizon}:${item.actionCode}`"
            >
              <strong>{{ item.title }}</strong>
              ：{{ item.facts[0] || item.explanation }}
              <button
                type="button"
                class="text-link"
                @click="
                  selected = item.horizon || 'SHORT';
                  selectedAction = item.actionCode || '';
                "
              >
                核对该周期方案 →
              </button>
            </li>
          </ul>
        </details>
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
              <p v-if="current.status === 'UNAVAILABLE'">{{ current.conclusion }}</p>
            </div>
            <StateBadge
              :value="current.status"
              :label="labels[current.status] || current.status"
              context="holding"
              :explanation="holdingStatusExplanation({ research: data, horizon: current })"
            />
          </header>
          <div
            class="holding-decision"
            data-testid="holding-decision"
          >
            <p>
              <strong>尚未持有这只股票：</strong>
              {{ entryDecision('OPEN') }}
            </p>
            <p>
              <strong>已经持有这只股票：</strong>
              {{ entryDecision('ADD') }}
            </p>
            <small>
              依据所选日期的已保存收盘数据判断；保存计划只留存观察方案，不代表已买入或系统确认可以买入。
            </small>
          </div>
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
          <section
            v-if="current.actions.length"
            class="holding-scenarios"
            aria-label="研究方案"
          >
            <div class="holding-scenarios__heading">
              <h4>选择观察方案</h4>
              <span>每次查看和保存一个方案，条件与价位分别记录。</span>
            </div>
            <div
              class="holding-action-selector"
              role="group"
              aria-label="选择观察方案"
            >
              <button
                v-for="item in current.actions"
                :key="item.code"
                type="button"
                :aria-pressed="action?.code === item.code"
                :data-select-action="item.code"
                @click="selectedAction = item.code"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ actionLabel(item) }}</span>
              </button>
            </div>
            <article
              v-if="action"
              :key="`${symbol}:${date}:${current.code}:${action.code}`"
              :data-action="action.code"
              class="holding-action"
            >
              <header>
                <h4>{{ action.title }}</h4>
                <StateBadge
                  :value="action.status"
                  :label="actionLabel(action)"
                  context="holding"
                  :explanation="
                    holdingStatusExplanation({ research: data, horizon: current, action })
                  "
                />
              </header>
              <p>{{ action.conclusion }}</p>
              <dl class="holding-action__prices">
                <div v-if="action.observationZone">
                  <dt>
                    {{
                      ['RISK_BLOCKED', 'WAIT', 'UNAVAILABLE'].includes(action.status)
                        ? '结构观察区间（当前未确认可买入）'
                        : '买入 / 加仓观察带（仍需复核）'
                    }}
                  </dt>
                  <dd class="reference-price">
                    {{ number(action.observationZone.lower) }}–{{
                      number(action.observationZone.upper)
                    }}
                    元
                  </dd>
                </div>
                <div
                  v-if="
                    action.referencePrice != null &&
                    action.referencePrice !== action.observationZone?.lower &&
                    action.referencePrice !== action.observationZone?.upper
                  "
                >
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
              <RiskReferenceGuide v-if="action.stopReference != null" />
              <p class="help-text">
                {{
                  action.conditionLogic === 'ALL'
                    ? '全部列示条件须同时满足；个人前提另行确认。'
                    : '任一已确认风险组合满足，即进入减仓或退出复核。'
                }}
              </p>
              <h5 class="holding-conditions-title">触发条件 · {{ conditionProgress(action) }}</h5>
              <ul class="holding-action__conditions">
                <li
                  v-for="condition in action.conditions"
                  :key="condition.label"
                >
                  <details class="holding-condition">
                    <summary>
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
                    </summary>
                    <span>实测：{{ condition.observed }}</span>
                    <span>要求：{{ condition.required }}</span>
                  </details>
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
              <footer class="holding-save">
                <div>
                  <strong>跟踪「{{ action.title }}」</strong>
                  <p>
                    仅保存当前方案的条件、价位和原始依据；其他方案不会一起保存。不代表可以买入，也不会下单。
                  </p>
                </div>
                <SaveResearchPlan
                  v-if="data.quoteDate === data.asOf && current.status !== 'UNAVAILABLE'"
                  :symbol="symbol"
                  :date="date"
                  :horizon="current.code"
                  :action-code="action.code"
                  :action-title="action.title"
                />
                <span
                  v-else
                  class="help-text"
                >
                  本期数据不足，补齐后才能保存观察方案。
                </span>
              </footer>
            </article>
          </section>
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
            <p>
              <TermHelp
                term="research-method-version"
                label="分析规则"
              />
              ：{{ researchMethodLabel(data.version) }}
            </p>
            <small>内部规则编号：{{ data.version }}</small>
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
  padding: 10px 14px;
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
.holding-scenarios__heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 12px;
}
.holding-scenarios__heading h4 {
  margin: 0;
  font-size: 14px;
}
.holding-scenarios__heading span {
  font-size: 12px;
  color: #738398;
}
.holding-action-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.holding-action-selector button {
  flex: 1 1 160px;
  min-width: 0;
  padding: 12px;
  background: #fff;
  border: 1px solid #dce5ed;
  border-radius: 8px;
  color: #536a80;
  text-align: left;
  cursor: pointer;
}
.holding-action-selector button[aria-pressed='true'] {
  background: #eaf2f4;
  border-color: #235970;
  color: #235970;
}
.holding-action-selector button:focus-visible,
.holding-horizon-selector button:focus-visible {
  outline: 2px solid #235970;
  outline-offset: 3px;
}
.holding-action-selector strong,
.holding-action-selector span {
  display: block;
}
.holding-action-selector span {
  margin-top: 6px;
  font-size: 12px;
}
.holding-save {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e2e9ef;
}
.holding-save > div:first-child {
  flex: 1 1 240px;
}
.holding-save strong {
  font-size: 13px;
}
.holding-save p {
  margin: 4px 0 0;
  color: #738398;
  font-size: 12px;
}
.holding-save :deep(.plan-save) {
  margin-top: 0;
  max-width: 100%;
}
.holding-save :deep(.el-button) {
  white-space: normal;
  height: auto;
  min-height: 36px;
  line-height: 1.6;
}
.holding-conditions-title {
  font-size: 13px;
  margin: 18px 0 10px;
}
.holding-condition summary {
  cursor: pointer;
  padding: 8px 0;
}
.holding-condition span {
  margin-left: 14px;
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
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
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
  padding: 0;
  list-style: none;
}
.holding-action__conditions li {
  border-bottom: 1px solid #eef2f6;
  margin-bottom: 0;
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
.holding-decision {
  margin: 16px 0 24px;
  padding: 16px 20px;
  border-radius: 12px;
  background: var(--surface-soft, #f6f8fb);
  line-height: 1.7;
}
.holding-decision p {
  margin: 0 0 8px;
}
.holding-decision small {
  color: var(--text-secondary, #64748b);
}
</style>
