<script setup lang="ts">
import { ref } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import {
  saveExperiment,
  getExperiments,
  getExperiment,
  reproduceExperiment,
} from '../../api/experiments';
import type { SignalStatisticsQuery } from '../../types/requests';
import type { ValidationExperiment, ExperimentReplay } from '../../types/experiments';
import { timestamp } from '../../utils/format';
const props = defineProps<{ request: SignalStatisticsQuery; synthetic: boolean }>();
const { data, error, loading, reload } = useResource(getExperiments, []);
const { busy, error: actionError, run } = useMutation();
const selected = ref<ValidationExperiment>();
const replay = ref<ExperimentReplay>();
async function save() {
  await run(async () => {
    selected.value = await saveExperiment(props.request);
    replay.value = undefined;
    await reload();
  });
}
async function inspect(id: number) {
  await run(async () => {
    selected.value = await getExperiment(id);
    replay.value = undefined;
  });
}
async function reproduce(id: number) {
  await run(async () => {
    replay.value = await reproduceExperiment(id);
  });
}
</script>
<template>
  <SectionCard
    title="已保存的验证实验"
    description="保存完整输入、原始阈值证据、数据修订与结果指纹；复算不使用当前行情。"
  >
    <template #actions>
      <el-button
        :disabled="synthetic || busy"
        data-testid="save-experiment"
        @click="save"
      >
        保存当前验证
      </el-button>
    </template>
    <p
      v-if="synthetic"
      class="help-text"
    >
      模拟模式不保存真实研究实验。
    </p>
    <el-alert
      v-if="error || actionError"
      :title="actionError || error"
      type="error"
      :closable="false"
    />
    <el-table
      :data="data?.items || []"
      :aria-busy="loading"
    >
      <el-table-column
        prop="id"
        label="编号"
        width="70"
      />
      <el-table-column
        prop="symbol"
        label="证券"
        min-width="130"
      />
      <el-table-column
        prop="availabilityMode"
        label="时点模式"
        min-width="180"
      />
      <el-table-column
        label="保存时间"
        min-width="180"
      >
        <template #default="scope">{{ timestamp(scope.row.createdAt) }}</template>
      </el-table-column>
      <el-table-column
        label="核验"
        min-width="150"
      >
        <template #default="scope">
          <el-button
            link
            :disabled="busy"
            @click="inspect(scope.row.id)"
          >
            查看冻结结果
          </el-button>
          <el-button
            link
            :disabled="busy"
            :data-testid="'reproduce-experiment-' + scope.row.id"
            @click="reproduce(scope.row.id)"
          >
            复算核对
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <p
      v-if="selected"
      class="help-text"
    >
      实验 #{{ selected.id }} · {{ selected.methodVersion }} · 输入指纹 {{ selected.inputHash }} ·
      原结果状态 {{ selected.result?.dataStatus || '尚无结果' }}
    </p>
    <p
      v-if="replay"
      role="status"
      data-testid="experiment-replay-result"
    >
      {{
        replay.inputVerified && replay.resultMatches
          ? '冻结输入核验通过，复算结果一致。'
          : '实验复算不一致，请检查方法与冻结输入。'
      }}
    </p>
  </SectionCard>
</template>
