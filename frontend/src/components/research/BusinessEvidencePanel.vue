<script setup lang="ts">
import { ref } from 'vue';
import { useMutation } from '../../composables/useMutation';
import { useAuth } from '../../composables/useAuth';
import { api, write } from '../../api/client';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { useResource } from '../../composables/useResource';
import { getBusinessEvidence } from '../../api/business-evidence';
import { number, sourceName } from '../../utils/format';
const props = defineProps<{ symbol: string; date: string; refreshToken: number }>();
const { user } = useAuth();
const sync = useMutation();
const syncNote = ref('');
async function synchronize() {
  const identity = `${props.symbol}:${props.date}`;
  syncNote.value = '';
  await sync.run(async () => {
    const result = await api<{ status: string; reason?: string }>(
      '/admin/financials/sync',
      write('POST', { symbol: props.symbol, date: props.date }),
    );
    if (identity !== `${props.symbol}:${props.date}`) return;
    syncNote.value = `已请求实际三表核验，${collectionLabels[result.status] || '请核对采集详情'}；完整性按下方各项判断，缓存不会改写原抓取时间。${result.reason || ''}`;
    await resource.reload();
  });
}
const resource = useResource((signal) => getBusinessEvidence(props.symbol, props.date, signal), {
  watch: [() => props.symbol, () => props.date, () => props.refreshToken],
  identity: () => `${props.symbol}:${props.date}`,
});
const auditLabels: Record<string, string> = {
  NEEDS_INPUT: '待补有效情景',
  INCOMPLETE: '资料未齐',
  INCONSISTENT: '材料数值不一致',
  CHECKED_WITH_LIMITS: '交叉核对完成，仍受方法边界限制',
};
const collectionLabels: Record<string, string> = {
  AVAILABLE: '核心财报可读，三表完整性另核验',
  UNAVAILABLE: '来源暂不可用',
  NOT_CHECKED: '尚未采集',
  DISABLED: '采集未启用',
};
const sourceLink = (url: string | null) => (url && /^https?:\/\//i.test(url) ? url : undefined);
</script>
<template>
  <SectionCard
    title="经营质量与估值核对"
    description="先看事实与相反证据，再看还缺什么；私人估值情景不能替代独立核验。"
  >
    <template #actions>
      <el-button
        v-if="user?.role === 'ADMIN'"
        :loading="sync.busy.value"
        @click="synchronize"
      >
        更新本股三表资料
      </el-button>
    </template>
    <p
      v-if="syncNote"
      role="status"
      class="help-text"
    >
      {{ syncNote }}
    </p>
    <p
      v-if="sync.error.value"
      role="alert"
      class="notice notice--warning"
    >
      {{ sync.error.value }}
    </p>
    <AsyncState
      :loading="resource.loading.value"
      :error="resource.error.value"
      :has-content="Boolean(resource.data.value)"
      @retry="resource.reload"
    >
      <template v-if="resource.data.value">
        <p class="help-text">
          截止 {{ resource.data.value.date }} · 财报期末
          {{ resource.data.value.period || '暂无' }} ·
          {{ sourceName(resource.data.value.source || undefined) }}
          <a
            v-if="sourceLink(resource.data.value.sourceUrl)"
            :href="sourceLink(resource.data.value.sourceUrl)"
            target="_blank"
            rel="noopener noreferrer"
            class="text-link"
          >
            查看供应商来源入口 →
          </a>
        </p>
        <div class="evidence-grid">
          <article>
            <h4>现金兑现与盈利质量</h4>
            <strong>
              {{
                resource.data.value.cashConversion == null
                  ? '待核验'
                  : number(resource.data.value.cashConversion) + ' 倍'
              }}
            </strong>
            <p>
              {{
                resource.data.value.operatingQuality?.cashBasis === 'CONSOLIDATED_PROFIT'
                  ? '合并经营现金流 ÷ 合并净利润；不单凭比例判断好坏。'
                  : '合并现金流与归母利润权属口径不同，仅作金额对照，暂不评现金兑现质量。'
              }}
            </p>
          </article>
          <article>
            <h4>同期间毛利率变化</h4>
            <strong>
              {{
                resource.data.value.marginChange == null
                  ? '待核验'
                  : number(resource.data.value.marginChange * 100) + ' 个百分点'
              }}
            </strong>
            <p>只比较同源、同累计期间上年值；不把半年报与三季报直接环比。</p>
          </article>
          <article>
            <h4>估值独立核验</h4>
            <strong>
              {{ auditLabels[resource.data.value.valuationStatus] || '证据未齐，尚未通过' }}
            </strong>
            <p>
              最近年度基本EPS
              {{ number(resource.data.value.disclosedAnnualEps) }}
              元；同行可比性、盈利假设及股本口径仍需补证。
            </p>
          </article>
        </div>
        <details
          v-if="resource.data.value.references?.length"
          class="reading-details"
        >
          <summary>核对本次计算采用的保存版本与来源</summary>
          <p class="help-text">
            供应商汇总数据不是已逐页核对的原始公告；在线入口可能返回后续修订。下列时间与指纹用于定位本次保存证据。
          </p>
          <article
            v-for="reference in resource.data.value.references"
            :key="reference.purpose"
          >
            <strong>
              {{ reference.purpose }} · {{ reference.period }} · 保存版本
              {{ reference.version ?? '未知' }}
            </strong>
            <p>
              供应商公开时点 {{ reference.publishedAt || '未知' }} · 可用时点
              {{ reference.availableAt || '按公开精度保守处理' }}
            </p>
            <p class="help-text">内容指纹 {{ reference.contentHash || '旧材料未保存指纹' }}</p>
            <a
              v-if="sourceLink(reference.sourceUrl)"
              :href="sourceLink(reference.sourceUrl)"
              target="_blank"
              rel="noopener noreferrer"
              class="text-link"
            >
              核对来源入口 →
            </a>
          </article>
        </details>
        <details
          v-if="resource.data.value.operatingQuality"
          class="reading-details"
          open
        >
          <summary>现金流背离与增长质量：哪些已有原文支持？</summary>
          <article
            v-for="metric in resource.data.value.operatingQuality.metrics"
            :key="metric.label"
            class="operating-metric"
          >
            <strong>
              {{ metric.label }}：{{
                metric.value == null
                  ? '待核验'
                  : metric.unit === 'RATIO'
                    ? number(metric.value * 100) + '%'
                    : number(metric.value) + ' 元'
              }}
            </strong>
            <p>{{ metric.meaning }}</p>
            <p class="help-text">原文字段：{{ metric.sourceField }}</p>
            <a
              v-if="sourceLink(metric.sourceUrl)"
              :href="sourceLink(metric.sourceUrl)"
              target="_blank"
              rel="noopener noreferrer"
              class="text-link"
            >
              核对该报表来源 →
            </a>
          </article>
          <p v-if="!resource.data.value.operatingQuality.metrics.length">
            现有原文尚不足以计算三表明细；不以零补齐。
          </p>
        </details>
        <h4>已观察到的事实与反证</h4>
        <ul v-if="resource.data.value.findings.length">
          <li
            v-for="fact in resource.data.value.findings"
            :key="fact"
          >
            {{ fact }}
          </li>
        </ul>
        <p
          v-else
          class="help-text"
        >
          尚无足够可比事实，不生成经营好坏结论。
        </p>
        <details
          class="reading-details"
          open
        >
          <summary>下一步重点核查什么？</summary>
          <ul>
            <li
              v-for="item in resource.data.value.unknowns"
              :key="item"
            >
              {{ item }}
            </li>
          </ul>
        </details>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.operating-metric {
  padding: 12px 0;
  border-bottom: 1px solid #edf1f5;
}
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.evidence-grid article {
  background: #f4f7fa;
  padding: 16px;
  border-radius: 8px;
}
h4 {
  margin: 0 0 10px;
  font-size: 14px;
}
p,
li {
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
li + li {
  margin-top: 8px;
}
@media (max-width: 800px) {
  .evidence-grid {
    grid-template-columns: 1fr;
  }
}
</style>
