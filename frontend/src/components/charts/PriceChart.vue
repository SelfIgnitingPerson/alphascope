<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import * as echarts from 'echarts/core';
import { CandlestickChart, LineChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { chartPrices } from '../../utils/price-series';
import type { DailyBar, Indicator } from '../../types/models';
echarts.use([
  CandlestickChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
  CanvasRenderer,
]);
const props = defineProps<{ bars: DailyBar[]; indicators: Indicator[] }>();
const container = ref<HTMLElement>();
const prices = computed(() => chartPrices(props.bars, props.indicators));
let chart: echarts.ECharts | undefined;
let observer: ResizeObserver | undefined;
function draw() {
  if (!chart) {
    return;
  }
  chart.setOption(
    {
      animation: false,
      color: ['#4777a0', '#c79a55'],
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
      legend: { data: ['日K', 'MA20', 'MA60'], top: 0, right: 12, itemGap: 22 },
      grid: { left: 60, right: 28, top: 50, bottom: 64 },
      xAxis: {
        type: 'category',
        data: props.bars.map((bar) => bar.trade_date),
        axisLine: { lineStyle: { color: '#dce3ec' } },
        axisLabel: { color: '#7b889b' },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        scale: true,
        name: '人民币元',
        splitLine: { lineStyle: { color: '#eef1f5' } },
        axisLabel: { color: '#7b889b' },
      },
      dataZoom: [
        { type: 'inside', start: 55, end: 100 },
        {
          type: 'slider',
          height: 18,
          bottom: 8,
          borderColor: '#edf0f5',
          fillerColor: 'rgba(40,91,120,.12)',
        },
      ],
      series: [
        {
          name: '日K',
          type: 'candlestick',
          data: prices.value.candles,
          itemStyle: {
            color: '#cf5c60',
            color0: '#289580',
            borderColor: '#cf5c60',
            borderColor0: '#289580',
          },
        },
        ...(['ma20', 'ma60'] as const).map((key) => ({
          name: key.toUpperCase(),
          type: 'line',
          showSymbol: false,
          connectNulls: false,
          lineStyle: { width: 1.4 },
          data: prices.value[key],
        })),
      ],
    },
    true,
  );
}
onMounted(() => {
  if (!container.value) {
    return;
  }
  chart = echarts.init(container.value);
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(container.value);
  draw();
});
watch(() => [props.bars, props.indicators], draw);
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <p
    v-if="prices.warning"
    class="notice notice--warning"
    role="status"
  >
    {{ prices.warning }}
  </p>
  <div
    ref="container"
    class="price-chart"
    role="img"
    aria-label="个股日K线与20日、60日均线"
  />
</template>
