<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import RuleStatusGuide from '../components/common/RuleStatusGuide.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { findTerm, researchTerms, searchTerms, termCategories } from '../content/research-terms';
import { statusHelp } from '../content/status-help';
defineOptions({ inheritAttrs: false });
const route = useRoute();
const query = ref('');
const category = ref('风险规则');
const sections = [
  '风险规则',
  ...termCategories.filter((item) => item !== '风险规则'),
  '数据与任务状态',
];
watch(
  () => route.query,
  (value) => {
    const term = findTerm(typeof value.term === 'string' ? value.term : undefined);
    query.value = term?.name || '';
    category.value = term?.category || (value.section === 'states' ? '数据与任务状态' : '风险规则');
  },
  { immediate: true },
);
const terms = computed(() =>
  category.value === '数据与任务状态' ? [] : searchTerms(query.value, category.value),
);
const termGroups = computed(() =>
  termCategories
    .map((name) => ({ name, items: terms.value.filter((term) => term.category === name) }))
    .filter((group) => group.items.length),
);
// 同一个状态可出现在多个业务场景，按用途分组阅读。
const statusGroups = [
  {
    name: '规则状态与风险级别',
    codes: [
      'TRIGGERED',
      'NOT_TRIGGERED',
      'INSUFFICIENT_DATA',
      'NOT_APPLICABLE',
      'HIGH',
      'MEDIUM',
      'LOW',
    ],
  },
  {
    name: '数据质量与覆盖',
    codes: [
      'UNKNOWN',
      'PARTIAL',
      'COMPLETE',
      'AVAILABLE',
      'UNAVAILABLE',
      'INSUFFICIENT_HISTORY',
      'NOT_ARCHIVED',
      'OBSERVED',
      'TRADING',
      'SUSPENDED',
      'STALE',
      'MISSING',
      'PROXY',
    ],
  },
  {
    name: '任务与通道',
    codes: [
      'SUCCESS',
      'RUNNING',
      'PENDING',
      'FAILED',
      'CANCEL_REQUESTED',
      'TIMEOUT_REQUESTED',
      'CANCELLED',
      'TIMED_OUT',
      'INTERRUPTED',
      'SKIPPED',
      'NOT_CHECKED',
      'NOT_READY',
      'UNSUPPORTED',
      'COOLDOWN',
      'ACCESS_RESTRICTED',
      'ACCESS_COOLDOWN',
      'RETRY_READY',
    ],
  },
  {
    name: '研究验证与条件策略',
    codes: [
      'SMALL_SAMPLE',
      'NO_TRIGGERED_SIGNALS',
      'WAITING_WINDOW',
      'SYNTHETIC',
      'DEMO',
      'CONDITIONAL_MATCH',
      'WAIT',
      'WATCH',
      'RISK_BLOCKED',
    ],
  },
];
const states = computed(() => {
  const text = query.value.trim().toLocaleLowerCase();
  return Object.entries(statusHelp).filter(
    ([code, value]) =>
      !text ||
      [code, value.label, value.summary, value.detail || '']
        .join(' ')
        .toLocaleLowerCase()
        .includes(text),
  );
});
const visibleStatusGroups = computed(() =>
  statusGroups
    .map((group) => ({
      ...group,
      items: states.value.filter(([code]) => group.codes.includes(code)),
    }))
    .filter(
      (group) =>
        group.items.length &&
        (!category.value ||
          (category.value === '风险规则'
            ? group.name === '规则状态与风险级别'
            : category.value === '数据与任务状态' && group.name !== '规则状态与风险级别')),
    ),
);
function reset() {
  query.value = '';
  category.value = '风险规则';
}
</script>
<template>
  <div class="page-stack research-help-view">
    <RouterLink
      to="/help"
      class="text-link terms-guide-link"
    >
      操作步骤与模块使用流程 →
    </RouterLink>
    <nav
      class="help-category-nav"
      aria-label="说明分类"
    >
      <button
        v-for="item in sections"
        :key="item"
        type="button"
        :aria-pressed="category === item"
        @click="
          category = item;
          query = '';
        "
      >
        {{ item === '风险规则' ? '规则状态与风险' : item }}
      </button>
    </nav>
    <section
      v-if="category === '风险规则' && !query"
      class="section-card"
    >
      <div class="section-card__body">
        <h2>先分清三件事</h2>
        <p>
          规则状态说“本期是否符合条件”，风险级别说“触发后要多重视”，数据状态说“资料是否够用”。这三件事不能互相代替。
        </p>
        <RuleStatusGuide />
        <p class="help-text">
          本页解释本系统实际口径，不会重新计算或改写历史报告。数值、阈值和具体缺项以对应报告冻结证据为准。
        </p>
      </div>
    </section>
    <section class="section-card">
      <div class="section-card__body">
        <div class="research-help-search">
          <label>
            搜索术语或状态
            <input
              v-model="query"
              @input="category = ''"
              type="search"
              placeholder="例如：MA20、数据不足、量比、ATR、毛利率"
              aria-label="搜索术语或状态"
            />
          </label>
          <label>
            术语分类
            <select
              v-model="category"
              aria-label="术语分类"
            >
              <option value="">全部分类</option>
              <option
                v-for="item in sections"
                :key="item"
                :value="item"
              >
                {{ item }}
              </option>
            </select>
          </label>
          <button
            type="button"
            class="text-link"
            @click="reset"
          >
            重置筛选
          </button>
        </div>
        <h2 v-if="category !== '数据与任务状态'">
          {{ category || '全部分类搜索结果' }}
          <small>{{ terms.length }}/{{ researchTerms.length }} 项</small>
        </h2>
        <p
          v-if="!terms.length && category !== '数据与任务状态'"
          class="help-text"
        >
          没有匹配的术语。可换一个关键词，或查看下方状态说明。
        </p>
        <section
          v-for="group in termGroups"
          :key="group.name"
          class="term-group"
        >
          <h3 v-if="!category">
            {{ group.name }}
            <small>{{ group.items.length }} 项</small>
          </h3>
          <div class="research-help-terms">
            <details
              v-for="term in group.items"
              :key="term.id"
              :id="`term-${term.id}`"
              :open="Boolean(query.trim())"
            >
              <summary>
                {{ term.name }}
                <small>{{ term.category }}</small>
              </summary>
              <p>{{ term.meaning }}</p>
              <p
                v-if="term.example"
                class="research-help-example"
              >
                例：{{ term.example }}
              </p>
              <p class="help-text">{{ term.note }}</p>
              <small>相关名称：{{ term.aliases.join('、') }}</small>
            </details>
          </div>
        </section>
      </div>
    </section>
    <section
      v-if="visibleStatusGroups.length"
      class="section-card"
      id="status-explanations"
    >
      <div class="section-card__body">
        <h2>
          状态说明
          <small>按使用场景分类</small>
        </h2>
        <p class="help-text">
          “可用”用于数据；通道页面的同代码显示“已连通”，只说明该能力最近一次请求可用。
        </p>
        <details
          v-for="group in visibleStatusGroups"
          :key="group.name"
          class="status-group"
          :open="
            category === '风险规则' || Boolean(query.trim()) || route.query.section === 'states'
          "
        >
          <summary>
            {{ group.name }}
            <small>{{ group.items.length }} 项</small>
          </summary>
          <div class="research-help-statuses">
            <article
              v-for="[code, state] in group.items"
              :key="code"
            >
              <StateBadge :value="code" />
              <p>{{ state.summary }}</p>
              <details class="status-detail">
                <summary>详细说明</summary>
                <p
                  v-if="state.detail"
                  class="help-text"
                >
                  {{ state.detail }}
                </p>
                <small>{{ code }}</small>
              </details>
            </article>
          </div>
        </details>
        <p
          v-if="!states.length"
          class="help-text"
        >
          没有匹配的状态。可清空搜索查看全部状态。
        </p>
      </div>
    </section>
    <p class="help-text">
      一般概念参考：
      <a
        href="https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/atr"
        target="_blank"
        rel="noopener noreferrer"
      >
        ATR
      </a>
      、
      <a
        href="https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/relative-strength-comparison"
        target="_blank"
        rel="noopener noreferrer"
      >
        相对强度
      </a>
      、
      <a
        href="https://www.sse.com.cn/aboutus/publication/monthly/explain/"
        target="_blank"
        rel="noopener noreferrer"
      >
        上交所指标说明
      </a>
      。本项目的窗口、规则和参数另行定义，并非这些来源认可其效果。
    </p>
  </div>
</template>
<style scoped>
.terms-guide-link {
  font-size: 13px;
  justify-self: start;
}
.help-category-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.help-category-nav button {
  border: 1px solid #dbe5ed;
  border-radius: 8px;
  padding: 10px 16px;
  background: #fff;
  color: #536579;
  cursor: pointer;
  font: inherit;
}
.help-category-nav button[aria-pressed='true'] {
  background: #e7f2f5;
  border-color: #28647b;
  color: #205b70;
  font-weight: 600;
}
.help-category-nav button:focus-visible {
  outline: 2px solid #28647b;
  outline-offset: 2px;
}
.term-group + .term-group {
  margin-top: 24px;
}
h3 {
  font-size: 15px;
}
.status-group {
  border-bottom: 1px solid #e2e8f0;
  padding: 14px 0;
}
.status-group > summary {
  margin-bottom: 12px;
}
.status-detail {
  font-size: 12px;
  color: #64748b;
}

h2 {
  font-size: 18px;
  margin: 0 0 16px;
}
p {
  line-height: 1.8;
}
small {
  color: #64748b;
  font-size: 12px;
  font-weight: normal;
}
h2 small {
  margin-left: 8px;
}
.research-help-search {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  margin: 0 0 24px;
}
label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: #536579;
}
label:first-child {
  flex: 1;
  min-width: 230px;
}
input,
select {
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  color: #24354b;
  font: inherit;
}
.research-help-search button {
  border: 0;
  background: none;
  cursor: pointer;
  padding: 10px 0;
}
.research-help-terms {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.research-help-terms details {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  align-self: start;
}
summary {
  cursor: pointer;
  font-weight: 600;
  line-height: 1.7;
}
summary small {
  display: block;
  margin-top: 4px;
}
.research-help-terms p {
  margin: 12px 0;
  font-size: 13px;
}
.research-help-example {
  background: #f1f5f9;
  border-radius: 6px;
  padding: 10px;
}
.research-help-statuses {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.research-help-statuses article {
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  padding: 14px;
}
.research-help-statuses p {
  margin: 10px 0;
  font-size: 13px;
}
@media (max-width: 1000px) {
  .research-help-statuses {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 700px) {
  .research-help-terms,
  .research-help-statuses {
    grid-template-columns: 1fr;
  }
}
</style>
