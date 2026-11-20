<script setup lang="ts">
import { computed } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import TermHelp from '../common/TermHelp.vue';
import StateBadge from '../common/StateBadge.vue';
import { useResource } from '../../composables/useResource';
import { getResearchScore, getUniverseCoverage } from '../../api/market';
const props = defineProps<{ date: string; refreshToken: number }>();
const { data, error, loading, reload } = useResource(
  (signal) => getResearchScore(props.date, signal),
  { watch: [() => props.date, () => props.refreshToken], identity: () => props.date },
);
const names: Record<string, string> = {
  trend: '指数趋势',
  turnoverDirection: '成交额与方向',
  breadth: '均线广度',
  themeRelativeStrength: '主题相对强度',
  leaderConfirmation: '领先样本确认',
};
const dimensionTerms: Record<string, string> = {
  trend: 'ma',
  turnoverDirection: 'volume',
  breadth: 'breadth',
  themeRelativeStrength: 'rs20',
};
const scopes: Record<string, string> = {
  FULL_A: '完整全A',
  SH_SZ_A: '沪深A股',
  INDEX_PROXY: '沪深300代理',
  DISCLOSED_RESEARCH_SAMPLE: '披露研究样本',
  UNKNOWN: '范围未确认',
};
const universe = useResource((signal) => getUniverseCoverage(props.date, signal), {
  watch: [() => props.date, () => props.refreshToken],
  identity: () => props.date,
});
const progress = computed(() => {
  const items = Object.entries(data.value?.dimensions ?? {});
  const confirmed = items.filter(([, dimension]) => dimension.status === 'AVAILABLE');
  return {
    available: confirmed.length,
    total: items.length,
    points: confirmed.reduce((sum, [, dimension]) => sum + (dimension.score ?? 0), 0),
    gaps: items
      .filter(([, dimension]) => dimension.status !== 'AVAILABLE')
      .map(
        ([key, dimension]) =>
          `${names[key] || key}（${dimension.status === 'PROXY' ? '只有样本口径' : '缺少合格输入'}）`,
      ),
  };
});
</script>
<template>
  <SectionCard
    title="五维规则研究"
    description="公式与覆盖门槛可核对，研究阈值尚未完成历史效果验证。"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <el-button
      v-if="error"
      @click="reload"
    >
      重新读取评分
    </el-button>
    <p
      v-if="loading"
      class="help-text"
    >
      正在核对已存输入。
    </p>
    <template v-if="data">
      <p>
        完整全A总分：{{ data.score === null ? '尚不能评定' : data.score.toFixed(1) }} ·
        <StateBadge :value="data.state" />
        · {{ data.modelVersion }}
      </p>
      <p
        v-if="data.score === null && progress.total"
        class="notice notice--subtle"
      >
        已核验 {{ progress.available }} / {{ progress.total }} 项。
        <template v-if="progress.available">
          已核验分项合计 {{ progress.points.toFixed(1) }} / {{ progress.available * 20 }} 分。
        </template>
        <template v-if="progress.gaps.length">待补 {{ progress.gaps.join('、') }}。</template>
      </p>
      <p
        v-if="universe.data.value"
        class="help-text"
      >
        已核对清单：沪市 {{ universe.data.value.exchangeMembers.SSE || 0 }}、深市
        {{ universe.data.value.exchangeMembers.SZSE || 0 }}、北交所
        {{ universe.data.value.exchangeMembers.BSE ?? '未知' }}。
      </p>
      <p
        v-if="universe.error.value"
        class="help-text"
      >
        覆盖核验读取失败：{{ universe.error.value }}
      </p>
      <ul
        v-if="universe.data.value?.missing.length"
        class="help-text"
      >
        <li
          v-for="gap in universe.data.value.missing"
          :key="gap"
        >
          {{ gap }}
        </li>
      </ul>
      <div class="observation-parts">
        <article
          v-for="(dimension, key) in data.dimensions"
          :key="key"
        >
          <h4>
            <TermHelp
              :term="dimensionTerms[key]"
              :label="names[key] || key"
            />
          </h4>
          <strong>
            {{ dimension.score === null ? '证据不足' : dimension.score.toFixed(1) + ' / 20' }}
          </strong>
          <p>
            {{ scopes[dimension.sourceScope] || dimension.sourceScope }} ·
            {{
              dimension.status === 'PROXY'
                ? '仅原范围展示'
                : dimension.status === 'AVAILABLE'
                  ? '满足分项定义'
                  : '输入尚不满足'
            }}
          </p>
          <p class="help-text">{{ dimension.formula }}</p>
          <p class="help-text">{{ dimension.evidence.reason }}</p>
        </article>
      </div>
      <ul class="help-text">
        <li
          v-for="item in data.limitations"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
    </template>
  </SectionCard>
</template>
