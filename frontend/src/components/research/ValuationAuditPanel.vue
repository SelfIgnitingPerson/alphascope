<script setup lang="ts">
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { useResource } from '../../composables/useResource';
import { getValuationAudit } from '../../api/business-evidence';
import { number } from '../../utils/format';
const props = defineProps<{ symbol: string; date: string; refreshToken: number }>();
const resource = useResource((signal) => getValuationAudit(props.symbol, props.date, signal), {
  watch: [() => props.symbol, () => props.date, () => props.refreshToken],
  identity: () => `${props.symbol}:${props.date}`,
});
const statuses: Record<string, string> = {
  NEEDS_INPUT: '待补有效情景',
  INCOMPLETE: '材料未齐',
  INCONSISTENT: '材料数值不一致',
  CHECKED_WITH_LIMITS: '资料交叉核对完成，仍有方法边界',
};
const safe = (url: string | null) => (url && /^https?:\/\//i.test(url) ? url : undefined);
</script>
<template>
  <SectionCard
    title="股本、盈利与同行独立核验"
    description="同行价格和财报从服务端实际记录读取；提高个人EPS或PE不能抬高独立约束的观察上限。"
  >
    <AsyncState
      :loading="resource.loading.value"
      :error="resource.error.value"
      :has-content="Boolean(resource.data.value)"
      @retry="resource.reload"
    >
      <template v-if="resource.data.value">
        <p>
          <strong>{{ statuses[resource.data.value.status] || '待核验' }}</strong>
          · 截止 {{ resource.data.value.date }}
        </p>
        <div class="audit-grid">
          <article>
            <span>真实滚动归母EPS</span>
            <strong>{{ number(resource.data.value.observedTtmEps) }} 元</strong>
          </article>
          <article>
            <span>有效同行PE下四分位</span>
            <strong>{{ number(resource.data.value.peerPeLowerQuartile) }} 倍</strong>
          </article>
          <article>
            <span>独立约束后观察上限</span>
            <strong class="reference-price">
              {{ number(resource.data.value.conservativeCap) }} 元
            </strong>
          </article>
        </div>
        <ul class="audit-checks">
          <li
            v-for="check in resource.data.value.checks"
            :key="check.label"
          >
            <strong>
              {{ check.passed === true ? '已核对' : check.passed === false ? '不一致' : '缺材料' }}
              · {{ check.label }}
            </strong>
            <p>{{ check.observed }}</p>
          </li>
        </ul>
        <details class="reading-details">
          <summary>同行为何纳入或排除？</summary>
          <article
            v-for="peer in resource.data.value.peers"
            :key="peer.symbol"
          >
            <strong>
              {{ peer.symbol }} · {{ peer.status === 'CHECKED' ? '口径检查通过' : '排除' }} · PE
              {{ number(peer.pe) }}
            </strong>
            <p>{{ peer.reason }}</p>
            <a
              v-if="safe(peer.financialUrl)"
              :href="safe(peer.financialUrl)"
              target="_blank"
              rel="noopener noreferrer"
              class="text-link"
            >
              实际财报来源 →
            </a>
            <a
              v-if="safe(peer.comparabilityUrl)"
              :href="safe(peer.comparabilityUrl)"
              target="_blank"
              rel="noopener noreferrer"
              class="text-link"
            >
              人工同行依据 →
            </a>
          </article>
          <p v-if="!resource.data.value.peers.length">
            请在下方情景中补充明确同行及面值依据；未登记和无实际财报的股票不会自动当作有效同行。
          </p>
        </details>
        <details class="reading-details">
          <summary>公式与独立核验的边界</summary>
          <p
            v-for="limit in resource.data.value.limitations"
            :key="limit"
          >
            {{ limit }}
          </p>
        </details>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.audit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.audit-grid article {
  padding: 14px;
  border-radius: 8px;
  background: #f4f7fa;
}
.audit-grid span,
.audit-grid strong {
  display: block;
}
.audit-grid span,
p {
  font-size: 13px;
  line-height: 1.8;
}
.audit-grid strong {
  margin-top: 8px;
}
.audit-checks {
  padding-left: 20px;
}
.audit-checks li {
  margin: 14px 0;
}
.audit-checks p {
  margin: 5px 0;
  overflow-wrap: anywhere;
}
article {
  overflow-wrap: anywhere;
}
article + article {
  margin-top: 10px;
}
@media (max-width: 850px) {
  .audit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
