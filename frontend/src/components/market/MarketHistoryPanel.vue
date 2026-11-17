<script setup lang="ts">
import { getOverview, getResearchScore, getUniverseCoverage } from '../../api/market';
import { fillMarketHistory } from '../../api/jobs';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import { useAuth } from '../../composables/useAuth';
import { ref, computed, watch } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { percent } from '../../utils/format';
const props = defineProps<{ date: string; refreshToken: number }>();
const { user } = useAuth();
const selectedDate = ref(props.date);
watch(
  () => props.date,
  (value) => {
    selectedDate.value = value;
  },
);
const score = useResource((signal) => getResearchScore(selectedDate.value, signal), {
  watch: [() => selectedDate.value, () => props.refreshToken],
  identity: () => selectedDate.value,
});
const coverage = useResource((signal) => getUniverseCoverage(selectedDate.value, signal), {
  watch: [() => selectedDate.value, () => props.refreshToken],
  identity: () => selectedDate.value,
});
const overview = useResource((signal) => getOverview(selectedDate.value, signal), {
  watch: [() => selectedDate.value, () => props.refreshToken],
  identity: () => selectedDate.value,
});
const observation = computed(() => overview.data.value?.observation);
const scoreMinimum = computed(() => score.data.value?.parameters.minimumCoverage ?? 0.95);
const observationMinimum = computed(() => observation.value?.analysis?.parameters.minimumCoverage);
const requiredCoverage = computed(() =>
  Math.max(scoreMinimum.value, observationMinimum.value ?? scoreMinimum.value),
);
const progress = computed(() => {
  const state = observation.value?.collection;
  if (state?.business_date !== selectedDate.value || !state.evidence_json) return null;
  try {
    const value = JSON.parse(state.evidence_json);
    if (
      !Number.isSafeInteger(value.completedFactors) ||
      !Number.isSafeInteger(value.totalFactors) ||
      value.totalFactors <= 0 ||
      value.completedFactors < 0 ||
      value.completedFactors > value.totalFactors
    )
      return null;
    return { completed: value.completedFactors, total: value.totalFactors };
  } catch {
    return null;
  }
});
const gap = computed(() =>
  typeof breadth.value?.trading === 'number' && typeof breadth.value?.valid60 === 'number'
    ? Math.max(0, Math.ceil(breadth.value.trading * requiredCoverage.value) - breadth.value.valid60)
    : null,
);
const mutation = useMutation();
const job = ref<number>();
const breadth = computed(
  () =>
    score.data.value?.dimensions.breadth?.evidence.observed as
      | { trading?: number; valid20?: number; valid60?: number; coverage60?: number }
      | undefined,
);
async function refreshCoverage() {
  await Promise.all([score.reload(), coverage.reload(), overview.reload()]);
}
async function fill() {
  await mutation.run(async () => {
    job.value = (await fillMarketHistory(selectedDate.value)).id;
  });
}
</script>
<template>
  <SectionCard
    title="沪深市场数据补齐"
    description="沪深观察与完整全A分开。北交所当日清单未核验时，全A评级继续保留未知。"
  >
    <label class="history-date">
      补齐目标日期
      <el-date-picker
        v-model="selectedDate"
        type="date"
        value-format="YYYY-MM-DD"
        :clearable="false"
      />
    </label>
    <AsyncState
      :loading="score.loading.value"
      :error="score.error.value"
      @retry="score.reload"
    >
      <template v-if="score.data.value">
        <p>
          研究日 {{ selectedDate }} · 已发布均线广度有效 {{ breadth?.valid60 ?? '待核对' }} /
          {{ breadth?.trading ?? '待核对' }} 只。
        </p>
        <p class="help-text">
          评分准入覆盖 {{ percent(scoreMinimum, 0) }}；分项观察覆盖
          {{ observationMinimum == null ? '待核对' : percent(observationMinimum, 0) }}。
          两项门槛分别核对，不会降低原观察门槛。
        </p>
        <p>
          距离已知较高门槛 {{ percent(requiredCoverage, 0) }} 仍差
          {{ gap ?? '待核对' }}
          只合格历史窗口；缺项不会补成零值。
        </p>
        <p
          v-if="progress"
          class="notice notice--subtle"
        >
          最近保存的核对进度 {{ progress.completed }} / {{ progress.total }} 项；
          此数包含不合格项，不能当作有效广度。采集结束并发布新截面后，上方有效数量才会更新。
          进度按批次保存，运行状态与具体失败请在任务中心核对。
        </p>
        <p
          v-if="overview.error.value"
          class="help-text"
        >
          采集进度暂未读到：{{ overview.error.value }}。已发布评分独立展示。
        </p>
        <el-button @click="refreshCoverage">重新核对已保存覆盖</el-button>
        <p class="help-text">
          工作量按缺失日期、因子缓存及供应商限制变化，不能保证固定完成时间。补齐任务复用同源缓存，失败后可在任务中心重试，不从头覆盖已有证据。
        </p>
        <p
          v-if="coverage.error.value"
          class="help-text"
        >
          完整范围读取失败：{{ coverage.error.value }}
        </p>
        <ul>
          <li
            v-for="v in coverage.data.value?.missing || []"
            :key="v"
          >
            {{ v }}
          </li>
        </ul>
        <el-button
          v-if="user?.role === 'ADMIN'"
          :loading="mutation.busy.value"
          @click="fill"
        >
          提交 {{ selectedDate }} 历史补齐任务
        </el-button>
        <p
          v-else
          class="help-text"
        >
          全市场补齐由管理员执行，避免每位用户重复向供应商请求。
        </p>
        <p
          v-if="mutation.error.value"
          role="alert"
        >
          {{ mutation.error.value }}
        </p>
        <p v-if="job">
          已提交任务 #{{ job }}。
          <RouterLink to="/jobs">查看进度、取消或重试 →</RouterLink>
        </p>
        <p class="help-text">
          任务完成只代表本次执行结束，请重新核对覆盖率；不自动获得完整全A证明。
        </p>
      </template>
    </AsyncState>
  </SectionCard>
</template>
