<script setup lang="ts">
import { getStatusHelp, ruleStatusCodes } from '../../content/status-help';
import StateBadge from './StateBadge.vue';
import TermHelp from './TermHelp.vue';
defineProps<{ compact?: boolean }>();
</script>
<template>
  <details
    class="rule-status-guide"
    :open="!compact"
  >
    <summary>规则状态与风险级别怎么读</summary>
    <dl>
      <div
        v-for="code in ruleStatusCodes"
        :key="code"
      >
        <dt><StateBadge :value="code" /></dt>
        <dd>{{ getStatusHelp(code).summary }}</dd>
      </div>
    </dl>
    <p>
      <TermHelp
        term="risk-level"
        label="风险级别（高／中／低）"
      />
      讲的是严重度，与本次是否触发分开。
    </p>
    <p class="rule-status-guide__example">
      例如“现金流恶化：未触发｜高”，表示高严重度规则本期未满足条件；不是“已出现高风险”，也不代表整只股票安全。点击任一状态或带问号的术语可查看解释。
    </p>
  </details>
</template>
<style scoped>
.rule-status-guide {
  border: 1px solid #dbe5ed;
  background: #f8fafc;
  border-radius: 10px;
  padding: 14px 16px;
  margin: 0 0 18px;
  font-size: 13px;
}
summary {
  cursor: pointer;
  color: #3e566e;
  font-weight: 600;
}
dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 22px;
  margin: 16px 0;
}
dl > div {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
dt {
  flex: 0 0 auto;
}
dd {
  margin: 0;
  color: #536579;
  line-height: 1.7;
}
p {
  margin: 8px 0 0;
  line-height: 1.8;
}
.rule-status-guide__example {
  color: #64748b;
}
@media (max-width: 760px) {
  dl {
    grid-template-columns: 1fr;
  }
}
</style>
