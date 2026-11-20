<script setup lang="ts">
import { ref, watch } from 'vue';
import { api, write, query } from '../../api/client';
import { today } from '../../utils/date';
import { percent } from '../../utils/format';
import { useAuth } from '../../composables/useAuth';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
const props = defineProps<{ refreshToken: number; date: string }>();
const date = ref(props.date || today());
watch(
  () => props.date,
  (selected) => {
    date.value = selected;
  },
);
const { user } = useAuth();
const job = ref<number>();
const mutation = useMutation();
const repairNote = ref('');
async function repairStored() {
  const dates = readiness.data.value?.missingDates.slice(0, 10) || [];
  if (!dates.length) return;
  await mutation.run(async () => {
    const result = await api<{ restored: number; reused: number; issues: string[] }>(
      '/admin/market/repair-stored',
      write('POST', { asOf: date.value, dates }),
    );
    repairNote.value = `已补核${result.restored}日，复用${result.reused}日。${result.issues.join('；')}`;
    await readiness.reload();
    await reload();
  });
}
type Observation = {
  status: string;
  businessDate: string;
  scope: string;
  turnover: { completeDays: number; status: string };
  breadth: {
    trading: number;
    valid20: number;
    valid60: number;
    ratio20?: number;
    ratio60?: number;
    status: string;
  };
  limitations: string[];
};
const { data, loading, error, reload } = useResource(
  (signal) =>
    api<Observation | null>('/market/full-observation?' + query({ date: date.value }), { signal }),
  { watch: [() => date.value, () => props.refreshToken], identity: () => date.value },
);
const readiness = useResource(
  (signal) =>
    api<{
      archivedDays: number;
      requiredDays: number;
      source?: string;
      factorCapability: boolean;
      missingDates: string[];
      nextSteps: string[];
      metrics?: {
        label: string;
        status: string;
        actual: number;
        required: number;
        reason: string;
      }[];
    }>('/market/readiness?' + query({ date: date.value }), { signal }),
  { watch: [() => date.value, () => props.refreshToken], identity: () => date.value },
);
async function collect() {
  await mutation.run(async () => {
    const result = await api<{ id: number }>(
      '/admin/jobs/market-full?' + query({ date: date.value }),
      write('POST'),
    );
    job.value = result.id;
  });
}
</script>
<template>
  <SectionCard
    title="三所全A证据核验"
    description="将同日沪深北清单、行情、停牌和同源历史窗口逐项核对，不拿当前名单倒填历史。"
  >
    <div
      v-if="readiness.data.value"
      class="notice notice--subtle"
    >
      <strong>
        历史归档 {{ readiness.data.value.archivedDays }} /
        {{ readiness.data.value.requiredDays }} 日 · 同源因子{{
          ['BIGQUANT', 'BAOSTOCK_SINA'].includes(readiness.data.value.source ?? '')
            ? '来自已核验的历史导入资料'
            : readiness.data.value.factorCapability
              ? '已接入，查看下方覆盖情况'
              : '能力待补'
        }}
      </strong>
      <ul>
        <li
          v-for="step in readiness.data.value.nextSteps"
          :key="step"
        >
          {{ step }}
        </li>
      </ul>
      <div
        v-if="readiness.data.value.metrics?.length"
        class="metric-readiness"
      >
        <article
          v-for="metric in readiness.data.value.metrics"
          :key="metric.label"
        >
          <strong>
            {{ metric.label }} · {{ metric.status === 'AVAILABLE' ? '该项可用' : '该项资料不足' }}
          </strong>
          <p>
            核验 {{ metric.actual }} / {{ metric.required || '应有数待确认' }} · {{ metric.reason }}
          </p>
        </article>
      </div>
      <p
        v-if="repairNote"
        class="help-text"
        role="status"
      >
        {{ repairNote }}
      </p>
      <el-button
        v-if="
          user?.role === 'ADMIN' &&
          readiness.data.value.source === 'EASTMONEY' &&
          readiness.data.value.missingDates.length
        "
        :loading="mutation.busy.value"
        @click="repairStored"
      >
        核验本机已存历史（最多10日）
      </el-button>
      <details v-if="readiness.data.value.missingDates.length">
        <summary>查看缺少的交易日期</summary>
        {{ readiness.data.value.missingDates.join('、') }}
      </details>
    </div>
    <p
      v-if="readiness.error.value"
      role="alert"
    >
      补齐进度暂不可读：{{ readiness.error.value }}
    </p>
    <div class="full-controls">
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        :clearable="false"
      />
      <el-button @click="date = today()">选择今天</el-button>
      <el-button
        @click="
          () => {
            reload();
            readiness.reload();
          }
        "
      >
        读取归档
      </el-button>
      <el-button
        v-if="user?.role === 'ADMIN'"
        :loading="mutation.busy.value"
        @click="collect"
      >
        核验所选日期三所数据
      </el-button>
    </div>
    <p
      v-if="job"
      role="status"
    >
      已提交共享采集任务 #{{ job }}，
      <RouterLink to="/jobs">查看进度 →</RouterLink>
      。完成后读取归档，不会自动生成日报。
    </p>
    <el-alert
      v-if="mutation.error.value"
      :title="mutation.error.value"
      type="error"
      :closable="false"
    />
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <template v-if="data">
        <p>
          {{ data.businessDate }} · {{ data.scope }} ·
          {{
            data.status === 'AVAILABLE' ? '分项证据达到门槛' : '仍有缺项，完整评级继续受准入约束'
          }}
        </p>
        <p>
          成交额连续历史 {{ data.turnover.completeDays }} / 20 日；MA20有效
          {{ data.breadth.valid20 }} / {{ data.breadth.trading }}，MA60有效
          {{ data.breadth.valid60 }} / {{ data.breadth.trading }}。
        </p>
        <p v-if="data.breadth.ratio20 != null || data.breadth.ratio60 != null">
          站上 MA20 {{ percent(data.breadth.ratio20, 2) }} · 站上 MA60
          {{ percent(data.breadth.ratio60, 2) }}
        </p>
        <ul>
          <li
            v-for="item in data.limitations"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
      </template>
      <p
        v-else
        class="notice notice--subtle"
      >
        所选日期尚无已核验的三所观察。日终归档可在 16:10
        后核验，缺失日期可通过授权的同日历史资料重建。
      </p>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.full-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
@media (max-width: 700px) {
  .full-controls {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
