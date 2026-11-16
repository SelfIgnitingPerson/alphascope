<script setup lang="ts">
import type { FailureDiagnosis } from '../../types/common';
defineProps<{ diagnostic?: FailureDiagnosis | null; context?: string; compact?: boolean }>();
</script>
<template>
  <details
    v-if="diagnostic"
    class="failure-explanation"
    :open="!compact"
  >
    <summary>{{ context ? context + ' · ' : '' }}{{ diagnostic.title }}</summary>
    <div
      v-if="diagnostic.providerError"
      class="provider-error"
      data-testid="provider-error"
    >
      <p>
        <strong>
          {{ diagnostic.providerError.provider }} · 错误码 {{ diagnostic.providerError.code }}
        </strong>
      </p>
      <p>代码释义：{{ diagnostic.providerError.meaning }}</p>
      <p v-if="diagnostic.providerError.messageState === 'UPSTREAM_SAFE_TEXT'">
        供应商原文：{{ diagnostic.providerError.message }}
      </p>
      <p
        v-else-if="diagnostic.providerError.messageState === 'WITHHELD'"
        class="provider-error__note"
      >
        供应商消息未命中安全模板，未公开原文；以上为代码释义。
      </p>
      <p
        v-else
        class="provider-error__note"
      >
        当时未保存供应商原文，以上为错误码释义，不补造原始消息。
      </p>
    </div>
    <dl>
      <div>
        <dt>原因</dt>
        <dd>{{ diagnostic.reason }}</dd>
      </div>
      <div>
        <dt>影响</dt>
        <dd>{{ diagnostic.impact }}</dd>
      </div>
      <div>
        <dt>建议操作</dt>
        <dd>{{ diagnostic.action }}</dd>
      </div>
    </dl>
    <details class="technical">
      <summary>技术信息（维护人员）</summary>
      <small>
        诊断代码：{{ diagnostic.code }}
        <span v-if="context">· 定位：{{ context }}</span>
      </small>
    </details>
  </details>
</template>
<style scoped>
.failure-explanation {
  margin: 12px 0;
  border: 1px solid #e2d9c9;
  border-radius: 8px;
  padding: 12px;
  background: #fffbf4;
  font-size: 13px;
  color: #39495c;
  overflow-wrap: anywhere;
}
.provider-error {
  border-bottom: 1px solid #e5dccb;
  padding-bottom: 10px;
}
.provider-error p {
  margin: 8px 0;
  line-height: 1.8;
}
.provider-error__note {
  color: #6d7785;
  font-size: 12px;
}
summary {
  cursor: pointer;
  font-weight: 600;
  line-height: 1.7;
}
dl {
  display: grid;
  gap: 10px;
  margin: 12px 0;
}
dl > div {
  display: grid;
  grid-template-columns: 4em minmax(0, 1fr);
  gap: 10px;
}
dt {
  color: #805b2d;
  font-weight: 600;
}
dd {
  margin: 0;
  line-height: 1.8;
}
small {
  color: #6d7785;
}
</style>
