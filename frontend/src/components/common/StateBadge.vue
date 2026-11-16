<script setup lang="ts">
import { computed } from 'vue';
import {
  getStatusHelp,
  ruleStatusCodes,
  type StatusContext,
  type StatusExplanation,
} from '../../content/status-help';
import HelpPopover from './HelpPopover.vue';
const props = withDefaults(
  defineProps<{
    value?: string | null;
    label?: string;
    context?: StatusContext;
    /** 当前这条记录的实际证据、缺项和处理入口。 */
    explanation?: StatusExplanation;
    /** 可点击的报告链接内禁用内嵌按钮，仍保留中文title说明。 */
    explain?: boolean;
  }>(),
  { context: 'general', explain: true },
);
const state = computed(() => getStatusHelp(props.value, props.context));
const helpTo = computed(
  () =>
    props.explanation?.next?.to ||
    ([...ruleStatusCodes, 'HIGH', 'MEDIUM', 'LOW'].includes(props.value || '')
      ? '/terms'
      : '/terms?section=states'),
);

const summary = computed(
  () => props.explanation?.diagnostic?.reason || props.explanation?.reason || state.value.summary,
);
const detail = computed(
  () => props.explanation?.action || props.explanation?.diagnostic?.action || state.value.detail,
);
const title = computed(() =>
  props.explanation?.subject
    ? `${props.explanation.subject} · ${props.label || state.value.label}`
    : `${props.label || state.value.label} · 状态说明`,
);
const nativeHelp = computed(() =>
  [
    summary.value,
    ...(props.explanation?.facts || []),
    ...(props.explanation?.missing || []),
    detail.value,
  ]
    .filter(Boolean)
    .join('；'),
);
</script>
<template>
  <HelpPopover
    v-if="explain"
    :label="label || state.label"
    :title="title"
    :summary="summary"
    :detail="detail"
    :help-to="helpTo"
    :help-label="explanation?.next?.label"
  >
    <template #details>
      <div
        v-if="explanation"
        class="state-help-evidence"
      >
        <p v-if="explanation.diagnostic?.providerError">
          <strong>
            供应商错误：{{ explanation.diagnostic.providerError.code }} ·
            {{ explanation.diagnostic.providerError.meaning }}
          </strong>
        </p>
        <p v-if="explanation.diagnostic?.providerError?.messageState === 'UPSTREAM_SAFE_TEXT'">
          供应商原文：{{ explanation.diagnostic.providerError.message }}
        </p>
        <p
          v-if="
            explanation.diagnostic?.providerError &&
            explanation.diagnostic.providerError.messageState !== 'UPSTREAM_SAFE_TEXT'
          "
        >
          {{
            explanation.diagnostic.providerError.messageState === 'NOT_RECORDED'
              ? '当时未保存供应商原文；以上为代码释义。'
              : '原文未公开；以上为代码释义。'
          }}
        </p>
        <template v-if="explanation.facts?.length">
          <strong>本条记录</strong>
          <ul>
            <li
              v-for="fact in explanation.facts"
              :key="fact"
            >
              {{ fact }}
            </li>
          </ul>
        </template>
        <template v-if="explanation.missing?.length">
          <strong>具体缺项 / 待核验</strong>
          <ul>
            <li
              v-for="item in explanation.missing"
              :key="item"
            >
              {{ item }}
            </li>
          </ul>
        </template>
      </div>
    </template>
    <span
      class="state-badge"
      :class="`state-badge--${state.tone}`"
    >
      {{ label || state.label }}
    </span>
  </HelpPopover>
  <span
    v-else
    class="state-badge"
    :class="`state-badge--${state.tone}`"
    :title="nativeHelp"
  >
    {{ label || state.label }}
  </span>
</template>

<style scoped>
.state-help-evidence {
  margin: 12px 0;
  padding: 10px 12px;
  background: #f5f8fb;
  border-radius: 8px;
  overflow-wrap: anywhere;
}
.state-help-evidence strong {
  font-size: 12px;
}
.state-help-evidence ul {
  padding-left: 18px;
  margin: 6px 0 12px;
}
.state-help-evidence li {
  margin: 5px 0;
}
</style>
