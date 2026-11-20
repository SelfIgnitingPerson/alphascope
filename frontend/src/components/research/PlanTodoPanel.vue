<script setup lang="ts">
import { ref, watch } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import { getPlanTodos, followupPlans, acknowledgePlan } from '../../api/plans';
import { planReviewLabels } from '../../content/plan-labels';
const props = defineProps<{ date: string; refreshToken: number }>();
const emit = defineEmits<{ changed: [] }>();
const feedback = ref('');
const submittedDate = ref('');
watch(
  () => props.date,
  () => {
    feedback.value = '';
  },
);
const resource = useResource((signal) => getPlanTodos(props.date, signal), {
  watch: [() => props.date, () => props.refreshToken],
  identity: () => props.date,
});
const mutation = useMutation();
async function followup() {
  const date = props.date;
  submittedDate.value = date;
  await mutation.run(async () => {
    const result = await followupPlans(date);
    if (props.date !== date) return;
    feedback.value = `本次已核对${result.reviewed}份，仍需处理${result.remaining}份。${result.issues.slice(0, 3).join('；')}`;
    await resource.reload();
    emit('changed');
  });
}
async function acknowledge(id: number) {
  const date = props.date;
  submittedDate.value = date;
  await mutation.run(async () => {
    await acknowledgePlan(id, date);
    if (props.date !== date) return;
    await resource.reload();
    emit('changed');
  });
}
</script>
<template>
  <SectionCard
    title="计划跟踪待办"
    description="汇总全部跟踪计划。收盘任务会自动复核；确认阅读只处理提醒，保留原始风险事实。"
  >
    <template #actions>
      <el-button
        :loading="mutation.busy.value"
        @click="followup"
      >
        按所选日批量复核
      </el-button>
    </template>
    <el-alert
      v-if="mutation.error.value && submittedDate === date"
      :title="mutation.error.value"
      type="error"
      :closable="false"
    />
    <p
      v-if="feedback"
      role="status"
      class="help-text"
    >
      {{ feedback }}
    </p>
    <AsyncState
      :loading="resource.loading.value"
      :error="resource.error.value"
      @retry="resource.reload"
    >
      <template v-if="resource.data.value">
        <div class="todo-counts">
          <span>
            <strong>{{ resource.data.value.risk || 0 }}</strong>
            风险触及
          </span>
          <span>
            <strong>{{ resource.data.value.changed || 0 }}</strong>
            条件变化
          </span>
          <span>
            <strong>{{ resource.data.value.data || 0 }}</strong>
            资料待补
          </span>
          <span>
            <strong>{{ resource.data.value.due || 0 }}</strong>
            尚待当日复核
          </span>
        </div>
        <p
          v-if="!resource.data.value.total"
          class="help-text"
        >
          保存个股观察方案后，这里会跟踪原条件与风险边界。
        </p>
        <ul
          v-else
          class="todo-items"
        >
          <li
            v-for="item in resource.data.value.items"
            :key="item.id"
          >
            <div>
              <RouterLink :to="{ path: '/plans', query: { plan: item.id } }">
                {{ item.name }} ·
                {{ planReviewLabels[item.latestReviewStatus || ''] || '尚待复核' }}
              </RouterLink>
              <p class="help-text">
                {{
                  item.latestReviewExplanation || '先按所选日核对原条件，尚无复核不表示可以建仓。'
                }}
              </p>
            </div>
            <el-button
              v-if="item.latestReviewDate && !item.acknowledged"
              :disabled="mutation.busy.value"
              @click="acknowledge(item.id)"
            >
              已阅读此项
            </el-button>
          </li>
        </ul>
        <p
          v-if="resource.data.value.total && !resource.data.value.items.length"
          class="help-text"
        >
          当前已存复核均已阅读；新增证据仍会重新提示。
        </p>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.todo-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 28px;
  margin-bottom: 16px;
  color: var(--text-muted);
}
.todo-counts strong {
  font-size: 22px;
  color: var(--text-primary);
  margin-right: 6px;
}
.todo-items {
  list-style: none;
  padding: 0;
  margin: 0;
}
.todo-items li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-top: 1px solid var(--border-color, #e2e8f0);
}
.todo-items p {
  margin: 6px 0 0;
}
</style>
