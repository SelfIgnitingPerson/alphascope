<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { getMarketUniverse } from '../../api/market';
import { fillMarketUniverse } from '../../api/jobs';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import { useAuth } from '../../composables/useAuth';
import { today } from '../../utils/date';
import { timestamp } from '../../utils/format';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
const props = defineProps<{ date: string; refreshToken: number }>();
const selectedDate = ref(props.date);
watch(
  () => props.date,
  (value) => {
    selectedDate.value = value;
  },
);
const { user } = useAuth();
const { data, loading, error, reload } = useResource(
  (signal) => getMarketUniverse(selectedDate.value, signal),
  {
    watch: [() => selectedDate.value, () => props.refreshToken],
    identity: () => selectedDate.value,
  },
);
const filter = ref('');
const visible = computed(() =>
  (data.value?.members || []).filter(
    (row) =>
      !filter.value.trim() ||
      (row.symbol + row.name).toLowerCase().includes(filter.value.trim().toLowerCase()),
  ),
);
const mutation = useMutation();
const job = ref<{ id: number; date: string }>();
async function collectCurrent() {
  const date = today();
  await mutation.run(async () => {
    const result = await fillMarketUniverse(date);
    job.value = { id: result.id, date };
    selectedDate.value = date;
    await reload();
  });
}
</script>
<template>
  <SectionCard
    title="北交所在市目录"
    description="单独核对成员名单。目录归档成功，不代表日线、停牌和历史均线窗口已完整。"
  >
    <div class="universe-controls">
      <label>
        目录日期
        <el-date-picker
          v-model="selectedDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
      </label>
      <el-button @click="selectedDate = today()">查看今天目录</el-button>
      <el-button @click="reload">读取已归档目录</el-button>
      <el-button
        v-if="user?.role === 'ADMIN'"
        :loading="mutation.busy.value"
        @click="collectCurrent"
      >
        采集当前北交所名单
      </el-button>
    </div>
    <p
      v-if="job"
      role="status"
    >
      已提交 {{ job.date }} 目录任务 #{{ job.id }}；后台核验后才发布名单。
      <RouterLink to="/jobs">查看进度及结果 →</RouterLink>
    </p>
    <p
      v-if="mutation.error.value"
      role="alert"
    >
      {{ mutation.error.value }}
    </p>
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <template v-if="data">
        <p
          v-if="data.status === 'AVAILABLE'"
          class="universe-result"
        >
          {{ data.businessDate }} · 已归档北交所在市成员
          <strong>{{ data.memberCount }} 只</strong>
          · 来源：新浪在市目录 · 核验于 {{ timestamp(data.observedAt) }}
        </p>
        <p
          v-else
          class="notice notice--subtle"
        >
          {{
            data.status === 'NOT_CONFIGURED'
              ? '当前未配置目录来源。'
              : `${data.businessDate} 没有目录归档。`
          }}
          采集按钮只采集上海当前日期，不会用今天名单补成所选历史日期。
        </p>
        <ul class="help-text">
          <li
            v-for="item in data.limitations"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
        <details v-if="data.status === 'AVAILABLE'">
          <summary>查看成员及清单证明</summary>
          <p class="help-text">
            归档 #{{ data.snapshotId }} ·
            <a
              :href="data.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              供应商目录入口
            </a>
          </p>
          <p class="universe-hash">SHA256：{{ data.contentHash }}</p>
          <el-input
            v-model="filter"
            placeholder="搜索名称或代码"
            clearable
            aria-label="筛选北交所成员"
          />
          <p class="help-text">
            匹配 {{ visible.length }} / {{ data.memberCount }} 只；搜索不会修改归档。
          </p>
          <el-table
            :data="visible"
            max-height="420"
            empty-text="没有匹配的成员"
          >
            <el-table-column
              prop="symbol"
              label="代码"
              width="150"
            />
            <el-table-column
              prop="name"
              label="名称"
            />
          </el-table>
        </details>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.universe-controls {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}
.universe-controls label {
  display: grid;
  gap: 6px;
  font-size: 13px;
}
.universe-result {
  line-height: 1.9;
}
.universe-hash {
  overflow-wrap: anywhere;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}
details {
  margin-top: 16px;
}
summary {
  cursor: pointer;
  margin-bottom: 14px;
}
@media (max-width: 600px) {
  .universe-controls {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
