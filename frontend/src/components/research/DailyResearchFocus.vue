<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { captureObservation } from '../../api/observations';
import { useMutation } from '../../composables/useMutation';
import { number, percent } from '../../utils/format';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { useResource } from '../../composables/useResource';
import { getResearchFocus } from '../../api/research-focus';
import type { PageProps } from '../../types/models';
const props = defineProps<PageProps>();
const page = ref(1);
const capture = useMutation();
const captureNote = ref('');
async function saveDay() {
  const identity = props.date;
  captureNote.value = '';
  await capture.run(async () => {
    const result = await captureObservation(identity);
    if (identity !== props.date) return;
    captureNote.value = `新增${result.saved}份，复用${result.reused}份。${result.issues.join('；') || (result.captureKind === 'RETROSPECTIVE' ? '所选日为事后回看，已留档但不会补造前瞻信号。' : '实际当日判断与执行协议已留档；旧记录保留。')}`;
    await resource.reload();
  });
}
const category = ref('ALL');
const expanded = ref(false);
watch([category, page, () => props.date], () => {
  expanded.value = false;
});
watch(
  () => props.date,
  () => {
    page.value = 1;
    category.value = 'ALL';
  },
);
const resource = useResource(
  (signal) => getResearchFocus({ asOf: props.date, page: page.value, size: 10 }, signal),
  {
    watch: [() => props.date, () => props.refreshToken, page],
    identity: () => `${props.date}:${page.value}`,
  },
);
const tabs = [
  { code: 'ALL', title: '全部事项' },
  { code: 'RISK', title: '优先核对' },
  { code: 'WATCH', title: '观察跟踪' },
  { code: 'MATCH', title: '条件满足' },
  { code: 'DATA_GAP', title: '资料待补' },
];
type FocusItem = NonNullable<Awaited<ReturnType<typeof getResearchFocus>>>['items'][number];
function matches(item: FocusItem, code: string) {
  const level = item.priorities?.[0]?.level;
  if (code === 'RISK') return item.category === 'RISK' && level !== 'WATCH';
  if (code === 'WATCH')
    return (
      item.category === 'WAIT' ||
      item.category === 'CHANGED' ||
      (item.category === 'RISK' && level === 'WATCH')
    );
  return code === 'ALL' || item.category === code;
}
const items = computed(() =>
  (resource.data.value?.items || []).filter((item) => matches(item, category.value)),
);
const visibleItems = computed(() => (expanded.value ? items.value : items.value.slice(0, 3)));
const count = (code: string) =>
  (resource.data.value?.items || []).filter((item) => matches(item, code)).length;
function positionLabel(item: FocusItem) {
  const impact = item.positionImpact;
  if (resource.data.value?.positionRiskEnabled === false) return '当前按自选观察';
  if (impact?.weight != null) return `占同日登记资产 ${percent(impact.weight)}`;
  if (impact?.state === 'FLAT') return '已登记空仓';
  if (impact?.state === 'HELD' || impact?.state === 'ESTIMATED')
    return '已登记持仓，仓位占比待核对';
  return '持仓影响待核对';
}

function invalidation(item: FocusItem) {
  if (item.positionImpact?.referenceState === 'BREACHED')
    return '原结构参考已触及，重新评估原计划。';
  if (item.originalPlanId) return '以原计划保存的价格、经营和失效条件为准。';
  return '打开对应持有周期核对失效条件。';
}
const leading = computed(() => resource.data.value?.items[0]);
</script>
<template>
  <SectionCard
    title="今天先看这几件事"
    description="先看前三项：发生了什么变化、对已登记持仓影响多大、下一步核对什么。轻重来自证据，不是亏损概率。"
  >
    <template #actions>
      <el-button
        :loading="capture.busy.value"
        @click="saveDay"
      >
        保存本日判断
      </el-button>
      <RouterLink
        :to="{ path: '/portfolio', query: { date } }"
        class="text-link"
      >
        更新我的持仓 →
      </RouterLink>
      <RouterLink
        to="/plans"
        class="text-link"
      >
        跟踪已保存计划 →
      </RouterLink>
    </template>
    <p
      v-if="captureNote"
      role="status"
      class="help-text"
    >
      {{ captureNote }}
    </p>
    <p
      v-if="capture.error.value"
      role="alert"
      class="notice notice--warning"
    >
      {{ capture.error.value }}
    </p>
    <AsyncState
      :loading="resource.loading.value"
      :error="resource.error.value"
      :has-content="Boolean(resource.data.value)"
      @retry="resource.reload"
    >
      <template v-if="resource.data.value">
        <p
          v-for="issue in resource.data.value.issues || []"
          :key="issue"
          class="notice notice--warning"
        >
          {{ issue }}
        </p>
        <p class="focus-scope">
          研究日 {{ resource.data.value.asOf }} · 本页核对 {{ resource.data.value.items.length }} /
          {{ resource.data.value.total }}
          只标的。先按风险级别排序。
          <strong v-if="resource.data.value.positionRiskEnabled === false">
            当前依据为自选和待处理原计划
          </strong>
          <strong v-else-if="resource.data.value.portfolioImpact?.knownWeight != null">
            当前依据包含已核对持仓
          </strong>
          <strong v-else>当前持仓影响待核对</strong>
        </p>
        <div
          v-if="resource.data.value.portfolioImpact"
          class="focus-portfolio"
        >
          <strong>我的已登记组合</strong>
          <p>
            正持仓 {{ resource.data.value.portfolioImpact.heldCount }} 只 · 日期或行情待核对
            {{ resource.data.value.portfolioImpact.pendingCount }} 只 · 已核对市值
            {{ number(resource.data.value.portfolioImpact.knownMarketValue) }} 元 ·
            占同日登记投资资产 {{ percent(resource.data.value.portfolioImpact.knownWeight) }}
          </p>
          <div class="focus-stress">
            <span>已登记现金 {{ number(resource.data.value.portfolioImpact.cashBalance) }} 元</span>
            <span>
              扣用款预留后现金 {{ number(resource.data.value.portfolioImpact.cashAfterReserve) }} 元
            </span>
            <span>
              已知股票共同下跌10%：约
              {{ number(resource.data.value.portfolioImpact.stress10Loss) }} 元
            </span>
            <span>
              共同下跌20%：约 {{ number(resource.data.value.portfolioImpact.stress20Loss) }} 元
            </span>
          </div>
          <p
            v-if="resource.data.value.portfolioImpact.referenceBudgetStatus === 'BREACHED'"
            class="reference-price"
          >
            已有持仓触及结构参考，不能以0元价差表示风险较低。
          </p>
          <details class="reading-details">
            <summary>查看相关风险分组与情景边界</summary>
            <p
              v-for="group in resource.data.value.portfolioImpact.riskGroups || []"
              :key="group.label"
            >
              {{ group.label }}：{{ number(group.marketValue) }} 元 · 占登记资产
              {{ percent(group.weight) }}
            </p>
            <p
              v-for="note in resource.data.value.portfolioImpact.limitations || []"
              :key="note"
            >
              {{ note }}
            </p>
          </details>
          <small>
            这是已知部分，不代表完整账户；行业集中度与关联敞口尚待补齐；用款前提需在持仓页登记并核对。超100%时请检查登记资产或持仓，不能作为加仓空间。
          </small>
        </div>
        <p
          v-else-if="resource.data.value.positionRiskEnabled !== false"
          class="help-text"
        >
          更新同日持仓与行情后可核对组合影响。
        </p>
        <p
          v-if="resource.data.value.knowledgeDate && resource.data.value.knowledgeDate !== date"
          class="help-text"
        >
          个人资料视角截至 {{ resource.data.value.knowledgeDate }}，用研究日
          {{ date }} 收盘估算当前影响；不代表该日历史账户。
        </p>
        <p class="help-text">
          变化口径优先采用上期实际留档；没有原留档时明确标记当前规则复算。保存本日判断只留存研究和执行假设，不等于保存个人交易计划。
        </p>
        <p
          v-if="leading"
          class="focus-leading"
        >
          <strong>本页先看：{{ leading.name }} · {{ leading.title }}</strong>
          <span>{{ leading.changeLabel || '请先核对当前依据' }}</span>
        </p>
        <div
          class="focus-filters"
          role="group"
          aria-label="筛选本页研究重点"
        >
          <button
            v-for="tab in tabs"
            :key="tab.code"
            type="button"
            :aria-pressed="category === tab.code"
            @click="category = tab.code"
          >
            {{ tab.title }} {{ count(tab.code) }}
          </button>
        </div>
        <div
          v-if="items.length"
          class="focus-list"
        >
          <article
            v-for="item in visibleItems"
            :key="item.symbol"
            :data-category="item.category"
            class="focus-item"
          >
            <div class="focus-item__name">
              <strong>{{ item.name }}</strong>
              <small>{{ item.symbol }}</small>
            </div>
            <div class="focus-item__body">
              <strong>{{ item.priorities?.[0]?.title || item.title }}</strong>
              <dl class="focus-checks">
                <dt>变化</dt>
                <dd>{{ item.changeLabel || item.explanation }}</dd>
                <dt>仓位影响</dt>
                <dd>{{ positionLabel(item) }}</dd>
                <dt>失效条件</dt>
                <dd>{{ invalidation(item) }}</dd>
                <dt>今天核对</dt>
                <dd>{{ item.priorities?.[0]?.nextChecks[0] || '核对行情日期和原始资料。' }}</dd>
              </dl>
              <details class="reading-details">
                <summary>查看证据、价格日期与核对条件</summary>
                <p
                  v-for="fact in item.priorities?.[0]?.facts || []"
                  :key="fact"
                >
                  {{ fact }}
                </p>
                <p v-if="item.priorities?.[0]?.explanation">{{ item.priorities[0].explanation }}</p>
                <p
                  v-for="check in [
                    ...(item.priorities?.[0]?.nextChecks.slice(1) || []),
                    ...(item.positionImpact?.checks || []),
                  ]"
                  :key="check"
                >
                  {{ check }}
                </p>
                <p v-if="item.positionImpact?.marketValue != null">
                  已登记市值 {{ number(item.positionImpact.marketValue) }} 元
                  <template v-if="item.positionImpact.unrealizedPnl != null">
                    · 账面盈亏 {{ number(item.positionImpact.unrealizedPnl) }} 元
                  </template>
                </p>
                <p>
                  行情 {{ item.quoteDate || '暂无' }}
                  <template v-if="item.comparisonDate">· 上期 {{ item.comparisonDate }}</template>
                  <template v-if="item.comparisonBasis === 'FROZEN_DAILY'">
                    · 对照上期实际留档
                  </template>
                  <template v-else-if="item.comparisonDate">· 按当前规则复算</template>
                </p>
              </details>
            </div>
            <RouterLink
              :to="
                item.originalPlanId
                  ? { path: '/plans', query: { date, plan: item.originalPlanId } }
                  : {
                      path: `/stocks/${item.symbol}`,
                      query: { date, ...(item.horizon ? { horizon: item.horizon } : {}) },
                    }
              "
              class="text-link"
            >
              {{ item.originalPlanId ? '查看原计划' : '查看依据' }} →
            </RouterLink>
          </article>
        </div>
        <p
          v-else
          class="help-text"
          role="status"
        >
          {{
            resource.data.value.total
              ? '本页没有此类事项，可切换分类或查看下一页。'
              : '还没有启用的自选股，先添加关注股票或登记持仓，再查看每日研究重点。'
          }}
          <RouterLink
            v-if="!resource.data.value.total"
            to="/watchlist"
            class="text-link"
          >
            添加自选 →
          </RouterLink>
        </p>
        <button
          v-if="items.length > 3"
          type="button"
          class="focus-expand"
          @click="expanded = !expanded"
        >
          {{ expanded ? '收起其余事项' : `展开本页其余${items.length - 3}项（含待核对线索）` }}
        </button>
        <div
          v-if="resource.data.value.total > resource.data.value.size"
          class="focus-pagination"
        >
          <span>第 {{ page }} 页</span>
          <el-button
            :disabled="page <= 1 || resource.loading.value"
            @click="
              page--;
              category = 'ALL';
            "
          >
            上一页
          </el-button>
          <el-button
            :disabled="
              page * resource.data.value.size >= resource.data.value.total || resource.loading.value
            "
            @click="
              page++;
              category = 'ALL';
            "
          >
            下一页
          </el-button>
        </div>
        <details class="reading-details">
          <summary>这些事项怎样判断？</summary>
          <p>
            优先核对已触及的风险参考、经营风险和已确认的风险组合。仅有弱走势或量能待确认时，继续跟踪。只有上期与本期同源、同方法且资料完整，才能标记条件“新满足”。每项都注明对照来自上期原判断还是当前规则复算。完整周期与条件见个股研究。
          </p>
        </details>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.focus-checks {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 7px 14px;
  margin: 10px 0;
}
.focus-checks dt {
  font-weight: 600;
}
.focus-checks dd {
  margin: 0;
}
.focus-portfolio {
  padding: 12px 14px;
  border: 1px solid #e2e9ef;
  border-radius: 8px;
  margin: 12px 0;
}
.focus-stress {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  font-size: 13px;
  margin: 10px 0;
}
.focus-scope {
  font-size: 12px;
  color: #738398;
  line-height: 1.8;
}
.focus-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}
.focus-filters button {
  border: 1px solid #e2e9ef;
  border-radius: 6px;
  background: #fff;
  color: #536a80;
  padding: 8px 12px;
  cursor: pointer;
}
.focus-filters button[aria-pressed='true'] {
  color: #235970;
  background: #eaf2f4;
  border-color: #235970;
}
.focus-item {
  display: grid;
  grid-template-columns: minmax(90px, 0.45fr) minmax(0, 3fr) auto;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #eef2f6;
  align-items: start;
}
.focus-item__name small {
  display: block;
  margin-top: 6px;
}
.focus-item small {
  color: #738398;
  font-size: 12px;
}
.focus-item strong {
  font-size: 14px;
}
.focus-item p {
  font-size: 13px;
  line-height: 1.8;
  margin: 7px 0;
}
.focus-item[data-category='RISK'] .focus-item__body > strong {
  color: #b14747;
}
.focus-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 16px;
}
.focus-leading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f4f7fa;
  font-size: 13px;
}
.focus-leading span {
  color: #738398;
  font-size: 12px;
}
.focus-expand {
  margin-top: 12px;
  padding: 9px 14px;
  background: #fff;
  border: 1px solid #dce5ed;
  border-radius: 6px;
  color: #235970;
  cursor: pointer;
}
@media (max-width: 700px) {
  .focus-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
}
</style>
