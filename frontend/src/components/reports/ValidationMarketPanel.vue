<script setup lang="ts">
import type { ValidationSummary } from '../../types/validation';
import { percent } from '../../utils/format';
defineProps<{ summary: ValidationSummary }>();
const labels: Record<string, string> = {
  NO_VALID_SCORES: '无完整评分',
  INSUFFICIENT_PAIRS: '无有效配对',
  SMALL_SAMPLE: '小样本',
  DESCRIPTIVE_ONLY: '仅描述统计',
};
</script>
<template>
  <template v-if="summary.coverageAtEnd">
    <details class="reading-details">
      <summary>
        全市场覆盖与评分
        <span>独立于下面的个股技术规则，展开核对缺项</span>
      </summary>
      <h3>完整市场覆盖</h3>
      <p>
        沪市 {{ summary.coverageAtEnd.exchangeMembers.SSE || 0 }} · 深市
        {{ summary.coverageAtEnd.exchangeMembers.SZSE || 0 }} · 北交所
        {{ summary.coverageAtEnd.exchangeMembers.BSE ?? '未知' }}；完整评分
        {{ summary.qualifiedScoreDays }} 天。
      </p>
      <ul class="help-text">
        <li
          v-for="gap in summary.coverageAtEnd.missing"
          :key="gap"
        >
          {{ gap }}
        </li>
      </ul>
      <el-table :data="summary.marketResults">
        <el-table-column label="区间">
          <template #default="s">
            {{ s.row.period === 'EARLY' ? '较早区间' : '较晚留出' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="horizon"
          label="交易日窗口"
        />
        <el-table-column
          prop="sampleCount"
          label="配对数量"
        />
        <el-table-column label="秩相关">
          <template #default="s">{{ s.row.rankCorrelation?.toFixed(4) ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="高分减低分收益">
          <template #default="s">{{ percent(s.row.highMinusLow, 2) }}</template>
        </el-table-column>
        <el-table-column label="样本状态">
          <template #default="s">{{ labels[s.row.status] || s.row.status }}</template>
        </el-table-column>
      </el-table>
    </details>
  </template>
</template>
