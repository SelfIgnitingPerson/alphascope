<script setup lang="ts">
import { computed, ref } from 'vue';
import SectionCard from '../components/common/SectionCard.vue';
import AsyncState from '../components/common/AsyncState.vue';
import ForecastCard from '../components/reports/ForecastCard.vue';
import { useResource } from '../composables/useResource';
import { getExpectations } from '../api/market';
import type { PageProps } from '../types/models';
const props = defineProps<PageProps>();
const { data, loading, error, reload } = useResource(
  (signal) => getExpectations(props.date, signal),
  { watch: [() => props.date, () => props.refreshToken], identity: () => props.date },
);
const selectedSymbol = ref('');
const selected = computed(
  () => data.value?.find((item) => item.symbol === selectedSymbol.value) || data.value?.[0],
);
</script>
<template>
  <div class="page-stack">
    <div class="notice notice--subtle">
      截至
      {{ date }}
      已保存的机构预测。预测快照从实际采集时刻起可用，与历史报告中当时可用的证据分别展示。
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <SectionCard
        v-if="data?.length"
        title="选择研究证券"
        description="每次展示一只证券的预测与观测证据，避免多份长卡片连续堆叠。"
      >
        <label class="filter-field">
          研究证券
          <el-select
            :model-value="selected?.symbol"
            aria-label="机构预测研究证券"
            @update:model-value="selectedSymbol = $event"
          >
            <el-option
              v-for="forecast in data"
              :key="forecast.symbol"
              :value="forecast.symbol"
              :label="`${forecast.name} · ${forecast.symbol}`"
            />
          </el-select>
        </label>
      </SectionCard>
      <ForecastCard
        v-if="selected"
        :key="selected.symbol"
        :forecast="selected"
      />
      <p
        v-if="data?.length === 0"
        class="help-text"
      >
        尚无启用的自选标的，请先添加自选并运行收盘研究任务。
      </p>
    </AsyncState>
  </div>
</template>
<style scoped>
:deep(.forecast-card + .forecast-card) {
  margin-top: 20px;
}
</style>
