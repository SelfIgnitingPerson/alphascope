<script setup lang="ts">
import ThemeObservation from '../components/themes/ThemeObservation.vue';
import ThemeLeaderPanel from '../components/themes/ThemeLeaderPanel.vue';
import SectionTabs from '../components/common/SectionTabs.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import TermHelp from '../components/common/TermHelp.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { computed, ref, watch } from 'vue';
import { getThemeStrength, getIndustryMetrics, getThemes } from '../api/market';
import { yearBefore } from '../utils/date';
import { number, percent, timestamp } from '../utils/format';
const props = defineProps<PageProps & { code: string }>();
const { data, loading, error, reload } = useResource(
  async (signal) => {
    const [strength, themes] = await Promise.all([
      getThemeStrength(props.code, { start: yearBefore(props.date), end: props.date }, signal),
      getThemes(signal),
    ]);
    return { strength, theme: themes.find((theme) => theme.code === props.code) };
  },
  {
    watch: [() => props.code, () => props.date, () => props.refreshToken],
    identity: () => `${props.code}:${props.date}`,
  },
);
const metrics = useResource((signal) => getIndustryMetrics(props.code, props.date, signal), {
  watch: [() => props.code, () => props.date, () => props.refreshToken],
  identity: () => `${props.code}:${props.date}`,
});
const sourceDefinition = computed(() => {
  try {
    const parsed = JSON.parse(data.value?.strength.cohort?.definition_json || '{}');
    return {
      definition: typeof parsed.definition === 'string' ? parsed.definition : '',
      pages: typeof parsed.sourcePages === 'string' ? parsed.sourcePages : '',
    };
  } catch {
    return { definition: '', pages: '' };
  }
});
const sourceUrl = computed(() => {
  const value = data.value?.strength.cohort?.source_url || '';
  return /^https?:\/\//i.test(value) ? value : '';
});
const activeTab = ref('members');
const tabs = [
  { id: 'members', label: '披露同业样本' },
  { id: 'history', label: '强弱历史' },
  { id: 'evidence', label: '样本来源与产业指标' },
];
const historyPage = ref(1);
const historyRows = computed(() => [...(data.value?.strength.items || [])].reverse());
watch([() => props.code, () => props.date], () => {
  historyPage.value = 1;
});
const latest = computed(() => data.value?.strength.items.at(-1));
</script>
<template>
  <div class="page-stack">
    <RouterLink
      to="/themes"
      class="back-link"
    >
      ← 返回 AI 产业链
    </RouterLink>
    <ThemeLeaderPanel
      :code="code"
      :research-date="date"
      :refresh-token="refreshToken"
    />
    <details
      class="reading-details"
      data-testid="legacy-theme-sample"
    >
      <summary>
        历史披露样本与联动观察
        <span>用于核对原有评分，不是上述当前龙头排名</span>
      </summary>
      <AsyncState
        :loading="loading"
        :error="error"
        @retry="reload"
      >
        <template v-if="data">
          <div class="detail-heading">
            <div>
              <span class="eyebrow">{{ code }}</span>
              <h2>{{ data.theme?.name || code }}</h2>
            </div>
            <StateBadge
              :value="
                latest?.is_synthetic
                  ? 'SYNTHETIC'
                  : latest?.score == null
                    ? 'INSUFFICIENT_DATA'
                    : 'OBSERVED'
              "
              :label="
                latest?.is_synthetic
                  ? '演示记录'
                  : !data.strength.members.length
                    ? '尚未建立样本'
                    : latest?.score == null
                      ? '样本数据不足'
                      : '已有样本观察'
              "
              :explain="false"
            />
          </div>
          <SectionCard
            title="当前样本观察"
            :description="data.strength.cohort?.title || '按当前登记成员读取；完整行业覆盖未核实。'"
          >
            <template v-if="data.strength.members.length">
              <ThemeObservation :strength="data.strength" />
              <p
                v-if="latest && latest.trade_date !== date"
                class="query-note"
              >
                所选日期 {{ date }}，最近保存的观测为
                {{ latest.trade_date }}。下列数值不代表所选日期的新观测。
              </p>
              <p class="query-note">
                已登记
                {{ data.strength.members.length }} 只成员。覆盖率的分母是本样本；完整行业覆盖{{
                  data.strength.industryCoverage === 'UNKNOWN' ? '尚未核实' : '以来源定义为准'
                }}，不能据此判断整个行业。
              </p>
            </template>
            <div
              v-else
              class="notice notice--warning"
            >
              <strong>该主题尚未建立有效研究样本</strong>
              <span>
                当前只有分类入口，没有可用成员。需要补充公开样本依据、真实成员和生效日期，再同步同源行情及技术指标；评分暂不生成。
              </span>
            </div>
          </SectionCard>
          <SectionTabs
            v-model="activeTab"
            label="主题研究内容分类"
            :items="tabs"
          >
            <template #members>
              <SectionCard
                title="披露同业研究样本（非排名）"
                description="这些是原有披露样本，不代表行业前几名；名单按所选日期有效，保留历史评分口径。"
              >
                <el-table
                  :data="data.strength.members"
                  empty-text="所选日期暂无有效成分"
                >
                  <el-table-column
                    prop="name"
                    label="名称"
                    min-width="100"
                  />
                  <el-table-column
                    label="代码"
                    min-width="130"
                  >
                    <template #default="s">
                      <RouterLink
                        :to="`/stocks/${s.row.symbol}`"
                        class="text-link"
                      >
                        {{ s.row.symbol }}
                      </RouterLink>
                    </template>
                  </el-table-column>
                </el-table>
              </SectionCard>
            </template>
            <template #history>
              <SectionCard
                title="主题强弱历史"
                description="最近日期优先；每页20个交易日。"
              >
                <el-table
                  :data="historyRows.slice((historyPage - 1) * 20, historyPage * 20)"
                  empty-text="暂无可复核的主题历史评分"
                >
                  <el-table-column
                    prop="trade_date"
                    label="日期"
                    min-width="120"
                  />
                  <el-table-column label="评分">
                    <template #default="s">{{ number(s.row.score, 1) }}</template>
                  </el-table-column>
                  <el-table-column label="RS20">
                    <template #header>
                      <TermHelp
                        term="rs20"
                        label="RS20"
                      />
                    </template>
                    <template #default="s">{{ percent(s.row.rs20) }}</template>
                  </el-table-column>
                  <el-table-column label="站上 MA20">
                    <template #header>
                      <TermHelp
                        term="breadth"
                        label="站上 MA20"
                      />
                    </template>
                    <template #default="s">{{ percent(s.row.breadth) }}</template>
                  </el-table-column>
                  <el-table-column label="覆盖率">
                    <template #header>
                      <TermHelp
                        term="coverage"
                        label="覆盖率"
                      />
                    </template>
                    <template #default="s">{{ percent(s.row.coverage) }}</template>
                  </el-table-column>
                </el-table>
                <el-pagination
                  v-model:current-page="historyPage"
                  :page-size="20"
                  :total="historyRows.length"
                  layout="total, prev, pager, next"
                />
              </SectionCard>
            </template>
            <template #evidence>
              <SectionCard
                title="样本定义与公开来源"
                description="样本来源说明成员选择依据，不是行业景气或未来订单的证明。"
              >
                <dl
                  v-if="data.strength.cohort"
                  class="reading-definitions"
                >
                  <div>
                    <dt>固定样本</dt>
                    <dd>
                      {{ data.strength.cohort.title }} · 定义成员
                      {{ data.strength.cohort.expected_members }} 只
                    </dd>
                  </div>
                  <div>
                    <dt>原始依据</dt>
                    <dd>
                      <a
                        v-if="sourceUrl"
                        :href="sourceUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="text-link"
                      >
                        {{ data.strength.cohort.source_title }} ↗
                      </a>
                      <span v-else>
                        {{ data.strength.cohort.source_title || '尚未记录可用来源' }}
                      </span>
                      <p v-if="sourceDefinition.pages">{{ sourceDefinition.pages }}</p>
                    </dd>
                  </div>
                  <div>
                    <dt>公开与可用</dt>
                    <dd>
                      公开 {{ timestamp(data.strength.cohort.source_published_at) }}；可用
                      {{ timestamp(data.strength.cohort.available_at) }}；成员生效
                      {{ data.strength.cohort.effective_from }}
                    </dd>
                  </div>
                  <div>
                    <dt>范围说明</dt>
                    <dd>
                      {{
                        sourceDefinition.definition || '原始定义未保存范围说明，请核对公开来源。'
                      }}
                    </dd>
                  </div>
                </dl>
                <p
                  v-else
                  class="query-note"
                >
                  当前没有可读取的公开样本定义。不能把分类名称当成已建立的行业研究。
                </p>
              </SectionCard>
              <SectionCard
                title="产业景气指标"
                description="订单、产量、价格、库存等独立产业证据；样本行情评分不能替代这些指标。"
              >
                <AsyncState
                  :loading="metrics.loading.value"
                  :error="metrics.error.value"
                  @retry="metrics.reload"
                >
                  <el-table
                    :data="metrics.data.value || []"
                    empty-text="尚未登记公开产业数据"
                  >
                    <el-table-column
                      prop="metric_code"
                      label="指标"
                    />
                    <el-table-column
                      prop="value"
                      label="数值"
                    />
                    <el-table-column
                      prop="unit"
                      label="单位"
                    />
                    <el-table-column
                      label="公开依据"
                      min-width="180"
                    >
                      <template #default="s">{{ s.row.title || '未记录标题' }}</template>
                    </el-table-column>
                    <el-table-column
                      prop="as_of_date"
                      label="数据日期"
                      min-width="120"
                    />
                  </el-table>
                  <p
                    v-if="metrics.data.value?.length === 0"
                    class="query-note"
                  >
                    尚未接入该主题的独立产业景气指标。目前只能查看成员行情与样本强弱，不能据此给出订单增长、供需改善或景气拐点结论。
                  </p>
                </AsyncState>
              </SectionCard>
            </template>
          </SectionTabs>
        </template>
      </AsyncState>
    </details>
  </div>
</template>
