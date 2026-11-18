<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { financialNumber, type FinancialMetricKey } from '../../utils/financial-analysis';
import type { Financial } from '../../types/fundamental';
echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer]);
const props = defineProps<{
  metric: FinancialMetricKey;
  label: string;
  rows: { year: number; row: Financial | null; comparableSource: boolean }[];
}>();
const container = ref<HTMLElement>();
const margin = computed(() => props.metric === 'gross_margin');
const values = computed(() =>
  props.rows.map(({ row, comparableSource }) => {
    const value = comparableSource ? financialNumber(row?.[props.metric]) : null;
    return value == null ? null : value / (margin.value ? 0.01 : 1e8);
  }),
);
let chart: echarts.ECharts | undefined;
let observer: ResizeObserver | undefined;
function draw() {
  if (!chart) return;
  chart.setOption(
    {
      animation: false,
      color: [margin.value ? '#9372ae' : '#477f98'],
      grid: { left: 58, right: 18, top: 30, bottom: 32 },
      tooltip: {
        trigger: 'axis',
        renderMode: 'richText',
        valueFormatter: (value: unknown) =>
          typeof value === 'number'
            ? `${value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}${margin.value ? '%' : '亿元'}`
            : '缺少同源有效值',
      },
      xAxis: {
        type: 'category',
        data: props.rows.map(({ year }) => String(year)),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#dce3ec' } },
        axisLabel: { color: '#738398' },
      },
      yAxis: {
        type: 'value',
        name: margin.value ? '%' : '亿元',
        ...(margin.value
          ? {
              min: Math.min(0, ...values.value.filter((v): v is number => v != null)),
              max: Math.max(100, ...values.value.filter((v): v is number => v != null)),
            }
          : {}),
        splitLine: { lineStyle: { color: '#eef1f5' } },
        axisLabel: { color: '#738398' },
      },
      series: [
        {
          name: props.label,
          type: margin.value ? 'line' : 'bar',
          data: values.value,
          barMaxWidth: 44,
          connectNulls: false,
          symbolSize: 7,
          itemStyle: margin.value
            ? {}
            : {
                color: (parameters: { value: unknown }) =>
                  typeof parameters.value === 'number' && parameters.value < 0
                    ? '#bd6570'
                    : '#477f98',
              },
        },
      ],
    },
    true,
  );
}
function resize() {
  // 页面用v-show切换标签：隐藏时不初始化0宽画布，显示后由尺寸观察器启动。
  if (!container.value?.clientWidth || !container.value.clientHeight) return;
  if (!chart) {
    chart = echarts.init(container.value);
    draw();
  }
  chart.resize();
}
onMounted(() => {
  if (container.value) {
    observer = new ResizeObserver(resize);
    observer.observe(container.value);
    resize();
  }
});
watch(() => [props.rows, props.metric], draw);
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div
    ref="container"
    class="financial-metric-chart"
    role="img"
    :aria-label="`${label}历年同累计报告期对比图，单位${margin ? '百分比' : '亿元'}`"
  />
  <p
    v-if="values.every((value) => value == null)"
    class="help-text"
  >
    此指标暂无有效值，缺失不按0绘制。
  </p>
</template>
<style scoped>
.financial-metric-chart {
  height: 230px;
  width: 100%;
}
</style>
