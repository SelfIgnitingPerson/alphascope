<script setup lang="ts">
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { ref, watch } from 'vue';
import { getReports } from '../api/reports';
import { timestamp } from '../utils/format';
import type { ReportType } from '../types/models';
const props = defineProps<PageProps>();
const type = ref<ReportType>('DAILY');
const page = ref(1),
  size = ref(20),
  start = ref('1990-01-01'),
  latestOnly = ref(true);
const total = ref(0);
watch([type, start, latestOnly, () => props.date], () => {
  page.value = 1;
  total.value = 0;
});
const { data, loading, error, reload } = useResource(
  (signal) =>
    getReports(
      {
        type: type.value,
        end: props.date,
        page: page.value,
        size: size.value,
        start: start.value,
        latestOnly: latestOnly.value,
      },
      signal,
    ),
  {
    watch: [type, page, size, start, latestOnly, () => props.date, () => props.refreshToken],
    identity: () =>
      JSON.stringify([
        type.value,
        page.value,
        size.value,
        start.value,
        latestOnly.value,
        props.date,
      ]),
  },
);
// 翻页期间保留同一筛选的总数，避免清空结果时分页器把当前页改回1。
watch(data, (value) => {
  if (value) total.value = value.total;
});
</script>
<template>
  <SectionCard
    title="报告归档"
    description="所选日期之前的研究报告；同日修订分别保留。"
  >
    <template #actions>
      <el-radio-group v-model="type">
        <el-radio-button value="DAILY">收盘日报</el-radio-button>
        <el-radio-button value="WEEKLY">每周周报</el-radio-button>
      </el-radio-group>
    </template>
    <div class="filter-bar filter-bar--compact">
      <label class="filter-field">
        开始日期
        <el-date-picker
          v-model="start"
          type="date"
          aria-label="报告开始日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
      </label>
      <div class="filter-field">
        <span>截止日期</span>
        <strong class="filter-value">{{ date }}</strong>
      </div>
      <el-switch
        v-model="latestOnly"
        active-text="同日只看最新版"
      />
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <div
        v-if="data?.items.length"
        class="report-list"
      >
        <RouterLink
          v-for="report in data?.items"
          :key="report.id"
          :to="`/reports/${report.id}`"
          class="report-list__item"
        >
          <div class="report-list__date">
            <strong>{{ report.business_date.slice(8) }}</strong>
            <span>{{ report.business_date.slice(0, 7) }}</span>
          </div>
          <div class="report-list__body">
            <h3>{{ report.business_date }} {{ type === 'DAILY' ? '收盘日报' : '每周周报' }}</h3>
            <p>
              第 {{ report.revision }} 版 ·
              {{ report.model === 'RULE_FALLBACK' ? '规则版报告' : '模型辅助摘要' }} · 数据截至
              {{ timestamp(report.data_as_of) }}
            </p>
          </div>
          <StateBadge
            :value="report.status"
            :explain="false"
          />
          <span class="report-list__arrow">↗</span>
        </RouterLink>
      </div>
      <el-empty
        v-else
        description="暂无报告，可在任务中心补跑所选日期"
      />
    </AsyncState>
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
    />
  </SectionCard>
</template>
