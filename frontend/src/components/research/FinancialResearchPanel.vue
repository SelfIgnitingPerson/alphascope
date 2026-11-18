<script setup lang="ts">
import { computed, ref } from 'vue';
import FinancialMetricChart from '../charts/FinancialMetricChart.vue';
import TermHelp from '../common/TermHelp.vue';
import type { Financial } from '../../types/fundamental';
import {
  analyzeFinancialReports,
  financialPeriods,
  type FinancialMetricComparison,
} from '../../utils/financial-analysis';
import { number, percent, sourceName, timestamp } from '../../utils/format';
const props = defineProps<{ items: Financial[]; date: string }>();
const selectedType = ref('');
const analysis = computed(() =>
  analyzeFinancialReports({ items: props.items, date: props.date, reportType: selectedType.value }),
);
const selected = computed({
  get: () => analysis.value.type,
  set: (value: string) => {
    selectedType.value = value;
  },
});
function amount(value: number | null, key: string) {
  return key === 'gross_margin'
    ? percent(value, 2)
    : value == null
      ? '—'
      : number(value / 1e8) + ' 亿元';
}
function difference(metric: FinancialMetricComparison) {
  if (metric.difference == null) return '';
  if (metric.key === 'gross_margin') return `${number(metric.difference * 100, 2)} 个百分点`;
  if (metric.growth != null) return percent(metric.growth, 2);
  return `变化 ${number(metric.difference / 1e8)} 亿元（上年同期${metric.previous === 0 ? '为0' : '为负数'}，不计算百分比增速）`;
}
const concerns = computed(() =>
  analysis.value.metrics.filter(
    (m) =>
      m.tone === 'negative' ||
      (['net_profit', 'operating_cashflow'].includes(m.key) && m.current != null && m.current < 0),
  ),
);
</script>
<template>
  <div
    v-if="analysis.latest"
    class="financial-research"
    data-testid="financial-research"
  >
    <div class="financial-period-filter">
      <label for="financial-period">比较口径</label>
      <el-select
        id="financial-period"
        v-model="selected"
        aria-label="选择累计财报口径"
      >
        <el-option
          v-for="type in analysis.types"
          :key="type"
          :value="type"
          :label="financialPeriods[type].label"
        />
      </el-select>
      <span>同一累计期间跨年比较，不把不同累计长度当成增长。</span>
    </div>
    <section
      class="financial-verdict"
      aria-label="经营表现说明"
      data-testid="financial-verdict"
    >
      <h3>{{ analysis.title }}</h3>
      <p>
        本期 {{ analysis.latest.period_end }}
        <template v-if="analysis.comparable">对比 {{ analysis.previous?.period_end }}</template>
        · {{ financialPeriods[analysis.type]?.label }} ·
        {{ sourceName(analysis.latest.source || undefined) }}
      </p>
      <div class="financial-verdict__parts">
        <div>
          <strong>改善的地方</strong>
          <ul v-if="analysis.positives.length">
            <li
              v-for="metric in analysis.positives"
              :key="metric.key"
            >
              {{ metric.label }}：{{ metric.change }}
              <template v-if="difference(metric)">，{{ difference(metric) }}</template>
            </li>
          </ul>
          <p v-else>
            {{
              analysis.comparable
                ? '已覆盖指标中暂无明确同比改善项。'
                : '同期对照不足，暂不判断改善项。'
            }}
          </p>
        </div>
        <div>
          <strong>需要关注的地方</strong>
          <ul v-if="concerns.length">
            <li
              v-for="metric in concerns"
              :key="metric.key"
            >
              {{ metric.label }}：{{ metric.change }}
              <template v-if="difference(metric)">，{{ difference(metric) }}</template>
              <template
                v-if="metric.current != null && metric.current < 0 && metric.tone !== 'negative'"
              >
                ；本期仍为负值
              </template>
            </li>
          </ul>
          <p v-else>
            {{
              analysis.comparable
                ? '这四项暂未显示明确同比恶化，不代表没有其他经营风险。'
                : '缺少完整同来源同期对照，暂不判断整体好坏。'
            }}
          </p>
        </div>
      </div>
      <p
        v-if="analysis.followUps.length"
        class="financial-primary-check"
      >
        <strong>本期先核对：</strong>
        {{ analysis.followUps[0] }}
      </p>
      <p class="help-text">
        {{ analysis.scope }}
        <template v-if="analysis.unknown.length">
          本次还有 {{ analysis.unknown.length }} 项缺少有效对照。
        </template>
      </p>
    </section>
    <div class="financial-comparison-grid">
      <article
        v-for="metric in analysis.metrics"
        :key="metric.key"
        class="financial-comparison"
        :data-metric="metric.key"
      >
        <header>
          <TermHelp
            :term="metric.term"
            :label="metric.label"
          />
          <span :class="`financial-change--${metric.tone}`">{{ metric.change }}</span>
        </header>
        <div class="financial-values">
          <strong>{{ amount(metric.current, metric.key) }}</strong>
          <span>同口径上年同期 {{ amount(metric.previous, metric.key) }}</span>
        </div>
        <p :class="`financial-change--${metric.tone}`">
          {{ difference(metric) || '缺项保留为空，不用0代替' }}
        </p>
        <FinancialMetricChart
          :metric="metric.key"
          :label="metric.label"
          :rows="analysis.history"
        />
      </article>
    </div>
    <p class="help-text">
      横轴为历年同报告期，最多展示最近10年。金额统一为亿元，各图纵轴独立；毛利率用百分比，变化用百分点。图中缺失或来源不一致的值留空，不连成趋势。
    </p>
    <section
      class="financial-follow-up"
      aria-label="后续关注事项"
    >
      <h3>后续重点看什么</h3>
      <ol>
        <li
          v-for="item in analysis.followUps"
          :key="item"
        >
          {{ item }}
        </li>
      </ol>
      <p class="help-text">
        当前已接入收入、归母净利润、经营现金流和毛利率；扣非利润、应收账款、存货、负债及原因说明尚需原始公告补充核对。
      </p>
      <a
        v-if="analysis.latest.source_url && /^https?:\/\//.test(analysis.latest.source_url)"
        :href="analysis.latest.source_url"
        target="_blank"
        rel="noopener noreferrer"
        class="text-link"
      >
        核对本期公开来源 →
      </a>
    </section>
    <p
      v-if="analysis.excluded"
      class="help-text"
    >
      {{ analysis.excluded }}
      条资料未进入图表对比：可能为不匹配期间、重复版本或所选日之后的数据；原始列表仍保留供核查。
    </p>
  </div>
  <p
    v-else
    class="help-text"
    role="status"
  >
    暂无可用的累计财报对比。请先核对所选日期、公开披露时间与报告期口径；不据此判断公司经营好坏。
  </p>
  <details
    class="reading-details"
    data-testid="financial-originals"
  >
    <summary>查看已披露原始财报（{{ items.length }}期）</summary>
    <p class="help-text">本表金额为人民币元；披露时间与内容版本保留供追溯。</p>
    <el-table
      :data="items"
      empty-text="尚未登记可用的公开财报"
    >
      <el-table-column
        prop="period_end"
        label="报告期"
        width="115"
      />
      <el-table-column
        label="累计口径"
        min-width="175"
      >
        <template #default="s">
          {{ financialPeriods[s.row.report_type]?.label || s.row.report_type }}
        </template>
      </el-table-column>
      <el-table-column
        label="营业收入（元）"
        min-width="175"
      >
        <template #default="s">{{ number(s.row.revenue) }}</template>
      </el-table-column>
      <el-table-column
        label="归母净利润（元）"
        min-width="175"
      >
        <template #default="s">{{ number(s.row.net_profit) }}</template>
      </el-table-column>
      <el-table-column
        label="经营现金流（元）"
        min-width="175"
      >
        <template #default="s">{{ number(s.row.operating_cashflow) }}</template>
      </el-table-column>
      <el-table-column
        label="毛利率"
        min-width="110"
      >
        <template #default="s">{{ percent(s.row.gross_margin, 2) }}</template>
      </el-table-column>
      <el-table-column
        label="披露时间与来源"
        min-width="200"
      >
        <template #default="s">
          {{ timestamp(s.row.published_at) }} · {{ sourceName(s.row.source) }} · 版本{{
            s.row.version ?? '未登记'
          }}
        </template>
      </el-table-column>
    </el-table>
  </details>
  <details class="reading-details">
    <summary>这项说明依据什么，不能说明什么？</summary>
    <p>
      以同来源、相同累计报告期的四项指标作方向性比较，没有设置盈利预测或综合买卖评分。负数和零基数不套用普通同比增速，毛利率差值以百分点表达。当前保存版本可能包含修订，不等于历史时点已知全部资料。
    </p>
    <p>
      收入增长不保证利润增长，利润与经营现金流也不等同。参考：
      <a
        href="https://www.sec.gov/about/reports-publications/beginners-guide-financial-statements"
        target="_blank"
        rel="noopener noreferrer"
        class="text-link"
      >
        财务报表阅读说明（SEC）
      </a>
      。
    </p>
  </details>
</template>
<style scoped>
.financial-research {
  display: grid;
  gap: 18px;
}
.financial-primary-check {
  border-left: 3px solid #b38a3e;
  padding: 8px 12px;
  background: #fffcf5;
}
.financial-period-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
}
.financial-period-filter label {
  font-size: 13px;
  font-weight: 600;
}
.financial-period-filter .el-select {
  width: 240px;
  max-width: 100%;
}
.financial-period-filter > span {
  font-size: 12px;
  color: #738398;
}
.financial-verdict {
  border: 1px solid #dce5ed;
  background: #f7fafc;
  border-radius: 9px;
  padding: 18px;
}
.financial-verdict h3,
.financial-follow-up h3 {
  font-size: 16px;
  margin: 0 0 10px;
}
.financial-verdict p,
.financial-verdict li,
.financial-follow-up li {
  font-size: 13px;
  line-height: 1.9;
}
.financial-verdict__parts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.financial-verdict__parts strong {
  font-size: 13px;
}
.financial-verdict__parts ul {
  padding-left: 18px;
}
.financial-comparison-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.financial-comparison {
  border: 1px solid #e2e9ef;
  border-radius: 9px;
  padding: 16px;
  min-width: 0;
}
.financial-comparison header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}
.financial-comparison header > span {
  font-size: 12px;
}
.financial-values {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 18px;
  margin-top: 16px;
}
.financial-values strong {
  font-size: 21px;
  color: #25344a;
}
.financial-values span {
  font-size: 12px;
  color: #738398;
}
.financial-comparison > p {
  font-size: 12px;
  min-height: 20px;
}
.financial-change--positive {
  color: #277e6d;
}
.financial-change--negative {
  color: #ad4b55;
}
.financial-change--neutral,
.financial-change--unknown {
  color: #738398;
}
.financial-follow-up {
  border-top: 1px solid #e2e9ef;
  padding-top: 18px;
}
.financial-follow-up ol {
  padding-left: 20px;
}
.financial-follow-up li {
  margin-bottom: 8px;
}
.help-text {
  font-size: 12px;
  line-height: 1.9;
  color: #738398;
}
@media (max-width: 760px) {
  .financial-comparison-grid,
  .financial-verdict__parts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
