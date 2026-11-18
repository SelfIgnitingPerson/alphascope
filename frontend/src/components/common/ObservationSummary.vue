<script setup lang="ts">
import { computed } from 'vue';
import type { ObservationSummaryData } from '../../types/research-priority';
const props = defineProps<{
  priority: ObservationSummaryData;
  compact?: boolean;
  context?: string;
}>();
const levels = { FIRST: '优先核对', IMPORTANT: '重点关注', WATCH: '观察跟踪', DATA: '资料待核' };
const facts = computed(() => props.priority.facts.slice(0, props.compact ? 2 : 3));
const checks = computed(() => props.priority.nextChecks.slice(0, props.compact ? 1 : 2));
const extra = computed(() => [
  ...props.priority.facts.slice(facts.value.length),
  ...props.priority.nextChecks.slice(checks.value.length),
]);
</script>
<template>
  <section
    class="observation-summary"
    :class="{ 'observation-summary--compact': compact }"
    :data-level="priority.level"
    data-testid="observation-summary"
    :aria-label="context || '观察重点与风险核对'"
  >
    <header>
      <span class="observation-summary__level">{{ levels[priority.level] }}</span>
      <h3>{{ priority.title }}</h3>
    </header>
    <p
      v-if="priority.explanation"
      class="observation-summary__reason"
    >
      {{ priority.explanation }}
    </p>
    <div class="observation-summary__body">
      <div>
        <h4>关键依据</h4>
        <ul v-if="facts.length">
          <li
            v-for="fact in facts"
            :key="fact"
          >
            {{ fact }}
          </li>
        </ul>
        <p v-else>本记录尚未保存可直接展开的数值依据，请核对完整证据。</p>
      </div>
      <div>
        <h4>下一步关注</h4>
        <ol v-if="checks.length">
          <li
            v-for="check in checks"
            :key="check"
          >
            {{ check }}
          </li>
        </ol>
        <p v-else>先核对该事项的日期与原始资料。</p>
      </div>
    </div>
    <details
      v-if="extra.length"
      class="observation-summary__more"
    >
      <summary>其余依据与核对条件（{{ extra.length }}项）</summary>
      <ul>
        <li
          v-for="item in extra"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
    </details>
  </section>
</template>
<style scoped>
.observation-summary {
  border: 1px solid #dce5ed;
  border-left: 4px solid #477f98;
  border-radius: 9px;
  background: #f7fafc;
  padding: 16px 18px;
  min-width: 0;
}
.observation-summary[data-level='FIRST'] {
  border-left-color: #b84c58;
  background: #fff8f8;
}
.observation-summary[data-level='IMPORTANT'] {
  border-left-color: #b38a3e;
  background: #fffcf5;
}
.observation-summary[data-level='DATA'] {
  border-left-color: #8b98a8;
  background: #f8f9fb;
}
.observation-summary header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
}
.observation-summary h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.6;
}
.observation-summary__level {
  font-size: 11px;
  font-weight: 600;
  color: #52758e;
  white-space: nowrap;
}
[data-level='FIRST'] .observation-summary__level {
  color: #a7424e;
}
[data-level='IMPORTANT'] .observation-summary__level {
  color: #9a742f;
}
.observation-summary__reason {
  margin: 8px 0 12px;
  font-size: 13px;
  line-height: 1.8;
  color: #536a80;
}
.observation-summary__body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}
.observation-summary h4 {
  margin: 0 0 6px;
  font-size: 12px;
  color: #60758b;
}
.observation-summary li,
.observation-summary__body p {
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.observation-summary ul,
.observation-summary ol {
  padding-left: 18px;
  margin: 0;
}
.observation-summary__more {
  margin-top: 10px;
  color: #60758b;
  font-size: 12px;
}
.observation-summary__more summary {
  cursor: pointer;
}
.observation-summary--compact {
  padding: 12px 14px;
}
.observation-summary--compact h3 {
  font-size: 14px;
}
.observation-summary--compact .observation-summary__reason {
  font-size: 12px;
}
@media (max-width: 700px) {
  .observation-summary__body {
    grid-template-columns: minmax(0, 1fr);
  }
  .observation-summary {
    padding: 14px;
  }
}
</style>
