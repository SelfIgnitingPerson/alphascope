<script setup lang="ts">
import { ref } from 'vue';
import { api, write, query } from '../../api/client';
import { today } from '../../utils/date';
import { useAuth } from '../../composables/useAuth';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
const props = defineProps<{ refreshToken: number }>();
const date = ref(today());
const { user } = useAuth();
const job = ref<number>();
const mutation = useMutation();
type Observation = {
  status: string;
  businessDate: string;
  scope: string;
  turnover: { completeDays: number; status: string };
  breadth: { trading: number; valid20: number; valid60: number; status: string };
  limitations: string[];
};
const { data, loading, error, reload } = useResource(
  (signal) =>
    api<Observation | null>('/market/full-observation?' + query({ date: date.value }), { signal }),
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
    <div class="full-controls">
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        :clearable="false"
      />
      <el-button @click="date = today()">选择今天</el-button>
      <el-button @click="reload">读取归档</el-button>
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
        所选日期尚无通过同日截面核验的三所观察。目录已保存不代表行情或历史窗口齐全；当日采集最早16:10后，历史日期只读取此前真实归档。
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
