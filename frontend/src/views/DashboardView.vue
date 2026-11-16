<script setup lang="ts">
import { computed } from 'vue';
import TermHelp from '../components/common/TermHelp.vue';
import MarketScorePanel from '../components/reports/MarketScorePanel.vue';
import MarketObservationCard from '../components/reports/MarketObservationCard.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { getOverview, getProviders, getBars } from '../api/market';
import { getWorkspaceStatus } from '../api/product';
import { getWatchlist } from '../api/portfolio';
import { getReports } from '../api/reports';
import { comparableReturn } from '../utils/price-series';
import { daysBefore } from '../utils/date';
import { number, percent, sourceName, timestamp } from '../utils/format';
const props = defineProps<PageProps>();
const changes = [() => props.date, () => props.refreshToken];
const query = { watch: changes, identity: () => props.date };
const readiness = useResource(getWorkspaceStatus, [() => props.refreshToken]);
const market = useResource((signal) => getOverview(props.date, signal), query);
const observedDate = computed(() => market.data.value?.observation?.analysis?.businessDate);
const historicalObservation = computed(() =>
  Boolean(observedDate.value && observedDate.value !== props.date),
);
const channels = useResource(getProviders, [() => props.refreshToken]);
const reports = useResource(
  (signal) => getReports({ type: 'DAILY', end: props.date, size: 5 }, signal),
  query,
);
const quotes = useResource(async (signal) => {
  const date = props.date;
  const watchlist = await getWatchlist(signal);
  return Promise.all(
    watchlist.map(async (stock) => {
      try {
        const response = await getBars(
          stock.symbol,
          { start: daysBefore(date, 14), end: date },
          signal,
        );
        const latest = response.items.at(-1);
        const previous = response.items.at(-2);
        const change = comparableReturn(latest, previous);
        return { ...stock, latest, change, previousDate: previous?.trade_date, error: '' };
      } catch (reason) {
        return {
          ...stock,
          latest: undefined,
          change: null,
          previousDate: undefined,
          error: reason instanceof Error ? reason.message : '此标的暂时无法加载',
        };
      }
    }),
  );
}, query);
</script>
<template>
  <div class="notice notice--subtle">
    <strong>研究使用顺序</strong>
    <span>先核对市场与数据，再选择关注股票，按持有周期制定行动计划。</span>
    <RouterLink
      to="/help"
      class="text-link"
    >
      新用户快速上手 →
    </RouterLink>
  </div>
  <div
    v-if="readiness.data.value?.issues.length"
    class="notice notice--warning"
    role="status"
  >
    <strong>当前数据需要核对</strong>
    <ul class="notice__content">
      <li
        v-for="issue in readiness.data.value.issues"
        :key="issue"
      >
        {{ issue }}
      </li>
    </ul>
    <RouterLink
      to="/jobs"
      class="text-link"
    >
      查看同步任务 →
    </RouterLink>
  </div>
  <div
    v-if="readiness.error.value"
    class="notice notice--subtle"
  >
    行情时效核查暂不可用；下方内容仍可独立查看。
    <el-button
      link
      @click="readiness.reload"
    >
      重试核查
    </el-button>
  </div>
  <div class="summary-grid">
    <article class="metric-card">
      <span class="metric-card__label">
        <TermHelp
          term="ma"
          label="沪深市场趋势"
        />
      </span>
      <strong>
        {{
          historicalObservation
            ? '本日观察待补'
            : market.data.value?.observation?.analysis?.trend.judgment || '等待市场观察'
        }}
      </strong>
      <span
        v-if="historicalObservation"
        class="metric-card__foot"
      >
        最近 {{ observedDate }}：{{
          market.data.value?.observation?.analysis?.trend.judgment
        }}，仅供历史参考
      </span>
      <span
        v-else
        class="metric-card__foot"
      >
        分项范围与数据日期见下方，缺项不生成全A评级
      </span>
    </article>
    <article class="metric-card">
      <span class="metric-card__label">关注标的</span>
      <strong>
        {{ quotes.data.value?.length ?? '—' }}
        <small>只</small>
      </strong>
      <span class="metric-card__foot">当前账号的自选研究范围</span>
    </article>
    <article class="metric-card">
      <span class="metric-card__label">
        <TermHelp
          term="coverage"
          label="已有行情覆盖"
        />
      </span>
      <strong>
        {{ channels.data.value?.latestData.length ?? '—' }}
        <small>个标的</small>
      </strong>
      <span class="metric-card__foot">已有数据数量不代表今日已更新</span>
    </article>
    <article class="metric-card">
      <span class="metric-card__label">最近日报</span>
      <strong class="metric-card__date">
        {{ reports.data.value?.items[0]?.business_date || '尚未生成' }}
      </strong>
      <span class="metric-card__foot">来源和采集日期可追溯</span>
    </article>
  </div>
  <SectionCard
    title="市场分项观察"
    description="行情服务异常时仍可独立查看自选和历史报告。"
  >
    <AsyncState
      :loading="market.loading.value"
      :error="market.error.value"
      :has-content="Boolean(market.data.value)"
      @retry="market.reload"
    >
      <div
        v-if="historicalObservation"
        class="notice notice--warning"
        role="status"
      >
        所选研究日为 {{ date }}，当前仅有
        {{ observedDate }} 市场观察。以下为历史参考，不能当作所选日的全市场判断。
        <RouterLink
          to="/jobs"
          class="text-link"
        >
          核对同步任务 →
        </RouterLink>
      </div>
      <MarketObservationCard
        v-if="market.data.value?.observation?.analysis"
        :observation="market.data.value.observation.analysis"
      />
      <el-empty
        v-else
        description="尚无所选业务日的市场观察，可在任务中心补跑"
      />
    </AsyncState>
  </SectionCard>
  <MarketScorePanel
    :date="date"
    :refresh-token="refreshToken"
  />
  <div class="dashboard-columns">
    <SectionCard
      title="关注标的"
      description="分别显示来源和日期；涨跌比较最近两条同源已存复权收盘，不保证为相邻交易日。"
    >
      <template #actions>
        <RouterLink
          to="/watchlist"
          class="text-link"
        >
          管理自选 →
        </RouterLink>
      </template>
      <AsyncState
        :loading="quotes.loading.value"
        :error="quotes.error.value"
        :has-content="Boolean(quotes.data.value)"
        @retry="quotes.reload"
      >
        <div
          v-if="quotes.data.value?.length"
          class="quote-list"
        >
          <RouterLink
            v-for="stock in quotes.data.value"
            :key="stock.symbol"
            :to="`/stocks/${stock.symbol}`"
            class="quote-row"
          >
            <div>
              <strong>{{ stock.name }}</strong>
              <small>{{ stock.symbol }}</small>
              <small
                v-if="stock.error"
                role="status"
              >
                {{ stock.error }}
              </small>
            </div>
            <div class="quote-row__price">
              <strong>{{ number(stock.latest?.close) }}</strong>
              <span
                :class="
                  stock.change === null ? 'muted' : stock.change >= 0 ? 'market-up' : 'market-down'
                "
              >
                {{ percent(stock.change, 2) }}
              </span>
              <small v-if="stock.change !== null">较 {{ stock.previousDate }} 收盘</small>
            </div>
            <div class="quote-row__source">
              <span>{{ sourceName(stock.latest?.source) }}</span>
              <small>{{ stock.latest?.trade_date || '等待首次采集' }}</small>
              <small v-if="stock.latest && stock.latest.trade_date !== date">
                与所选日期不同，请核对交易日及同步状态
              </small>
            </div>
          </RouterLink>
        </div>
        <div
          v-else
          class="notice"
        >
          <strong>开始你的第一份研究</strong>
          <p>先搜索并添加自选，提交首次初始化后，在任务中心查看进度与研究报告。</p>
          <RouterLink
            to="/watchlist"
            class="text-link"
          >
            添加自选 →
          </RouterLink>
        </div>
      </AsyncState>
    </SectionCard>
    <SectionCard
      title="数据与运行"
      description="已保存内容仍可读取，通道状态不等于行情已更新。"
    >
      <template #actions>
        <RouterLink
          to="/channels"
          class="text-link"
        >
          查看通道 →
        </RouterLink>
      </template>
      <AsyncState
        :loading="channels.loading.value"
        :error="channels.error.value"
        :has-content="Boolean(channels.data.value)"
        @retry="channels.reload"
      >
        <div
          v-for="channel in channels.data.value?.channels"
          :key="channel.channel"
          class="channel-preview"
        >
          <div>
            <strong>{{ sourceName(channel.channel) }}</strong>
            <small>
              {{
                channel.lastSuccess
                  ? `最近成功 ${timestamp(channel.lastSuccess)}`
                  : '尚无本次进程成功记录'
              }}
            </small>
          </div>
          <StateBadge
            :value="channel.state"
            context="channel"
          />
        </div>
        <RouterLink
          to="/jobs"
          class="secondary-link"
        >
          进入任务中心 →
        </RouterLink>
      </AsyncState>
    </SectionCard>
  </div>
  <SectionCard
    title="最新研究报告"
    description="只显示当前账号的报告，故障时可重试本区块。"
  >
    <template #actions>
      <RouterLink
        to="/reports"
        class="text-link"
      >
        全部报告 →
      </RouterLink>
    </template>
    <AsyncState
      :loading="reports.loading.value"
      :error="reports.error.value"
      :has-content="Boolean(reports.data.value)"
      @retry="reports.reload"
    >
      <div
        v-if="reports.data.value?.items.length"
        class="report-grid"
      >
        <RouterLink
          v-for="report in reports.data.value.items.slice(0, 3)"
          :key="report.id"
          :to="`/reports/${report.id}`"
          class="report-preview"
        >
          <span class="eyebrow">每日收盘研究 / 第 {{ report.revision }} 版</span>
          <h3>{{ report.business_date }} 日报</h3>
          <p>
            {{
              report.model === 'RULE_FALLBACK'
                ? '依据已计算证据生成的规则版报告'
                : '模型辅助整理的证据摘要'
            }}
          </p>
          <StateBadge
            :value="report.status"
            :explain="false"
          />
        </RouterLink>
      </div>
      <el-empty
        v-else
        description="所选日期之前暂无日报"
      />
    </AsyncState>
  </SectionCard>
</template>
