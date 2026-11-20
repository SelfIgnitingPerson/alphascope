<script setup lang="ts">
import { ref } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import { getForwardSummary, evaluateForward } from '../../api/observations';
import { number, percent, sourceName } from '../../utils/format';
import { planActionLabels, researchMethodLabel } from '../../content/plan-labels';
const props = defineProps<{
  date: string;
  refreshToken: number;
  symbol?: string;
  planId?: number;
}>();
const resource = useResource(
  (signal) =>
    getForwardSummary({ asOf: props.date, symbol: props.symbol, planId: props.planId }, signal),
  {
    watch: [() => props.date, () => props.refreshToken, () => props.symbol, () => props.planId],
    identity: () => `${props.date}:${props.symbol}:${props.planId}`,
  },
);
const mutation = useMutation();
const note = ref('');
const exclusionLabels: Record<string, string> = {
  WINDOW_NOT_MATURE: '观察窗口尚未满',
  CALENDAR_UNAVAILABLE: '交易日历待核对',
  AWAITING_EVALUATION: '已到期，待核验行情和成交条件',
  OVERLAPPING_SIGNAL: '同股票窗口重叠',
  WAITING_OR_NOT_EVALUATED: '尚未成熟或待核验',
};
const noEntryLabels: Record<string, string> = {
  RISK_BLOCKED: '入场风险条件触发',
  WAITING_FOR_CONDITIONS: '等待价格、成交或趋势条件',
  ENTRY_EVIDENCE_INSUFFICIENT: '入场依据待补',
  NO_ENTRY_ACTION: '当前范围没有建仓或加仓规则',
};
async function evaluate() {
  const date = props.date;
  note.value = '';
  await mutation.run(async () => {
    const result = await evaluateForward(date);
    if (date !== props.date) return;
    note.value = `新增${result.saved}个窗口、复用${result.reused}个。${result.issues.join('；') || '已核验成熟窗口；未成熟和不满足条件的记录不生成交易成绩。'}`;
    await resource.reload();
  });
}
</script>
<template>
  <SectionCard
    title="实际留档后的前瞻验证"
    description="由服务端按规则版本和冻结执行协议累计，不随复核翻页变化。读页面不重算、不写入。"
  >
    <template #actions>
      <el-button
        :loading="mutation.busy.value"
        @click="evaluate"
      >
        核验本人已成熟窗口
      </el-button>
    </template>
    <p
      v-if="note"
      role="status"
      class="help-text"
    >
      {{ note }}
    </p>
    <p
      v-if="mutation.error.value"
      role="alert"
      class="notice notice--warning"
    >
      {{ mutation.error.value }}
    </p>
    <AsyncState
      :loading="resource.loading.value"
      :error="resource.error.value"
      :has-content="Boolean(resource.data.value)"
      @retry="resource.reload"
    >
      <template v-if="resource.data.value">
        <p class="help-text">
          截止 {{ resource.data.value.date }} · 实际留档
          {{ resource.data.value.observationCount || 0 }} 份 · 满足入场条件
          {{ resource.data.value.candidateObservations }} 份 · 验证分组
          {{ resource.data.value.groups.length }} 个
        </p>
        <p
          v-if="resource.data.value.truncated"
          class="notice notice--warning"
        >
          当前服务返回的范围已截断，统计覆盖已读取的观察。
        </p>
        <p
          v-if="!resource.data.value.groups.length"
          class="notice notice--subtle"
        >
          <template v-if="!resource.data.value.observationCount">
            这个范围还没有符合条件的当日留档。可在收盘后到首页保存当日判断。
            <span v-if="planId">原计划只统计保存后、同规则版本的当日判断。</span>
          </template>
          <template v-else>已有当日留档，但没有满足建仓或加仓条件的信号。</template>
        </p>
        <ul
          v-if="Object.keys(resource.data.value.noEntryReasons || {}).length"
          class="help-text"
        >
          <li
            v-for="(count, reason) in resource.data.value.noEntryReasons"
            :key="reason"
          >
            {{ noEntryLabels[reason] || reason }} · {{ count }} 份
          </li>
        </ul>
        <article
          v-for="group in resource.data.value.groups"
          :key="`${group.methodVersion}:${group.source}:${group.horizon}:${group.actionCode}:${group.protocolVersion}`"
          class="forward-group"
        >
          <h4>
            {{ researchMethodLabel(group.methodVersion) }} ·
            {{ planActionLabels[group.actionCode] || '入场条件' }} · {{ sourceName(group.source) }}
          </h4>
          <p class="help-text">
            执行协议 {{ group.protocolVersion }} · 同股票重叠窗口排除；不同版本不合并。
          </p>
          <div class="forward-grid">
            <article
              v-for="window in group.windows"
              :key="window.windowDays"
            >
              <h4>{{ window.windowDays }} 个交易日</h4>
              <p>
                {{ window.status === 'COLLECTING' ? '样本积累中' : '仅描述已观察样本' }} ·
                已冻结可试算 {{ window.calculated }} / 成熟尝试 {{ window.attempted }}
              </p>
              <dl>
                <dt>扣费平均收益</dt>
                <dd>{{ percent(window.meanNetReturn) }}</dd>
                <dt>正收益比例</dt>
                <dd>{{ percent(window.positiveRatio) }}</dd>
                <dt>平均盈亏比</dt>
                <dd>{{ number(window.profitLossRatio) }}</dd>
                <dt>最差样本收盘回撤</dt>
                <dd>{{ percent(window.worstDrawdown) }}</dd>
                <dt>相对指数价格收益差</dt>
                <dd>{{ percent(window.meanExcessReturn) }}（{{ window.benchmarkSamples }}例）</dd>
              </dl>
              <details class="reading-details">
                <summary>哪些没有计入？</summary>
                <p v-if="!Object.keys(window.exclusions).length">本窗口无额外排除记录。</p>
                <p
                  v-for="(count, reason) in window.exclusions"
                  :key="reason"
                >
                  {{ exclusionLabels[reason] || reason }} · {{ count }} 项
                </p>
              </details>
            </article>
          </div>
        </article>
        <details class="reading-details">
          <summary>执行假设与样本边界</summary>
          <p
            v-for="item in resource.data.value.limitations"
            :key="item"
          >
            {{ item }}
          </p>
          <p>佣金、印花税与滑点是保存时冻结的试算假设；不是收益承诺，也不能证明提高了实盘胜率。</p>
        </details>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.forward-group {
  margin-top: 20px;
  border-top: 1px solid #e2e9ef;
  padding-top: 16px;
}
.forward-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.forward-grid > article {
  padding: 14px;
  background: #f4f7fa;
  border-radius: 8px;
  overflow-wrap: anywhere;
}
h4 {
  font-size: 14px;
  margin: 0 0 10px;
}
p {
  font-size: 13px;
  line-height: 1.8;
}
dl {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  font-size: 13px;
}
dd {
  margin: 0;
  font-weight: 600;
}
@media (max-width: 900px) {
  .forward-grid {
    grid-template-columns: 1fr;
  }
}
</style>
