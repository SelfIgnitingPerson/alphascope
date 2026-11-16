<script setup lang="ts">
import { computed } from 'vue';
import type { ReportAnalysis, ReportAssessment } from '../../types/models';
import StateBadge from '../common/StateBadge.vue';
import TermHelp from '../common/TermHelp.vue';
import RuleStatusGuide from '../common/RuleStatusGuide.vue';
import { ruleTermIds } from '../../content/research-terms';
import ResearchAdviceCard from './ResearchAdviceCard.vue';
import PriceVolumeCard from './PriceVolumeCard.vue';
import PriceReferenceSummary from './PriceReferenceSummary.vue';
import ThemeLinkageCard from './ThemeLinkageCard.vue';
import MarketObservationCard from './MarketObservationCard.vue';
import ForecastCard from './ForecastCard.vue';
import HistoricalEvidenceCard from './HistoricalEvidenceCard.vue';
import { number, percent, sourceName } from '../../utils/format';
const props = defineProps<{
  analysis: ReportAnalysis;
  weekly: boolean;
  reportDate?: string;
  section?: 'overview' | 'stocks' | 'market' | 'all';
  symbol?: string;
}>();
const emit = defineEmits<{ selectStock: [symbol: string] }>();
const show = (...sections: string[]) =>
  !props.section || props.section === 'all' || sections.includes(props.section);
const overviewStocks = computed(() =>
  [...props.analysis.stocks].sort(
    (left, right) => Number(right.triggered > 0) - Number(left.triggered > 0),
  ),
);
const visibleStocks = computed(() =>
  props.symbol
    ? props.analysis.stocks.filter((stock) => stock.symbol === props.symbol)
    : props.analysis.stocks,
);
const totalTriggered = computed(() =>
  props.analysis.stocks.reduce((total, s) => total + s.triggered, 0),
);
const totalMissing = computed(() =>
  props.analysis.stocks.reduce((total, s) => total + s.insufficient, 0),
);
const rules = (symbol: string, triggered: boolean) =>
  props.analysis.assessments.filter(
    (r) => r.symbol === symbol && (triggered ? r.status === 'TRIGGERED' : r.status !== 'TRIGGERED'),
  );
function metric(value: ReportAssessment['evidence'][number]) {
  return value.unit === 'PERCENT'
    ? percent(value.value, 2)
    : number(value.value) + (value.unit === 'TIMES' ? ' 倍' : value.unit === 'CNY' ? ' 元' : '');
}
const money = (value: number | null) =>
  value == null ? '暂无' : number(value / 100000000) + ' 亿元';
const financial = (symbol: string) => props.analysis.financials?.find((f) => f.symbol === symbol);
const priceVolume = (symbol: string) =>
  props.analysis.priceVolume?.find((item) => item.symbol === symbol);
const advice = (symbol: string) => props.analysis.advice?.find((a) => a.symbol === symbol);
</script>
<template>
  <div class="report-facts">
    <section
      v-if="show('overview')"
      class="report-focus"
    >
      <span class="eyebrow">本期重点 · 冻结证据分析</span>
      <h3>{{ analysis.headline }}</h3>
      <div class="report-focus__counts">
        <span>
          关注标的
          <strong>{{ analysis.stocks.length }}</strong>
        </span>
        <span>
          触发记录
          <strong>{{ totalTriggered }}</strong>
        </span>
        <span>
          证据不足记录
          <strong>{{ totalMissing }}</strong>
        </span>
        <span>
          沪深300{{ weekly ? '本周' : '当日' }}
          <strong>{{ percent(analysis.benchmarkReturn, 2) }}</strong>
        </span>
      </div>
      <p class="help-text">
        {{
          analysis.researchMode === 'WATCHLIST'
            ? '自选研究模式 · 不评估个人持仓风险。'
            : '关注标的研究。'
        }}
        全市场评级缺少完整维度，已取得的市场截面与个股证据分别展示。
      </p>
    </section>
    <section
      v-if="section === 'overview'"
      class="report-stock-overview"
    >
      <h3>个股关注重点</h3>
      <p class="help-text">点击股票查看量价策略和规则依据；未触发不代表没有风险。</p>
      <div class="report-stock-overview__grid">
        <button
          v-for="stock in overviewStocks"
          :key="stock.symbol"
          :class="{ 'report-stock-overview__alert': stock.triggered > 0 }"
          type="button"
          @click="emit('selectStock', stock.symbol)"
        >
          <strong>
            {{ stock.name }}
            <small>{{ stock.symbol }}</small>
          </strong>
          <span>{{ stock.triggered }} 项已触发 · {{ stock.insufficient }} 项证据不足</span>
          <p>
            {{
              rules(stock.symbol, true)
                .slice(0, 3)
                .map((rule) => rule.ruleName)
                .join('、') ||
              (stock.triggered > 0
                ? '本修订未列出触发明细，请核对个股研究。'
                : '本期没有已触发规则，仍需核对数据覆盖。')
            }}
          </p>
          <span class="text-link">查看个股研究 →</span>
        </button>
      </div>
    </section>
    <RuleStatusGuide
      v-if="show('stocks')"
      :compact="section === 'stocks'"
    />
    <PriceReferenceSummary
      v-if="show('overview') && analysis.priceVolume?.length"
      :studies="analysis.priceVolume"
    />
    <HistoricalEvidenceCard
      v-if="
        show('market') && analysis.marketAcquisition?.some((item) => item.mode === 'STORED_HISTORY')
      "
      :acquisition="analysis.marketAcquisition"
    />
    <template v-if="show('market')">
      <MarketObservationCard
        v-if="analysis.marketObservation"
        :observation="analysis.marketObservation"
      />
      <template
        v-for="forecast in analysis.forecasts"
        :key="forecast.symbol"
      >
        <ForecastCard
          v-if="forecast.latest"
          :forecast="forecast"
        />
      </template>
      <RouterLink
        v-if="analysis.forecasts"
        to="/expectations"
        class="text-link"
      >
        查看机构预测（按页面日期展示，与本历史报告分别保存） →
      </RouterLink>
      <section
        v-if="analysis.market?.amount != null"
        class="report-stock-fact"
      >
        <header class="report-stock-fact__header">
          <div>
            <h3>沪深A股当日市场截面</h3>
            <p>不含北交所 · {{ sourceName(analysis.market.source) }} · 按当日证券清单核验</p>
          </div>
          <StateBadge :value="analysis.market.status" />
        </header>
        <dl class="report-metric-grid">
          <div>
            <dt>{{ analysis.market.status === 'PARTIAL' ? '已覆盖成交额' : '成交额' }}</dt>
            <dd>{{ money(analysis.market.amount) }}</dd>
          </div>
          <div>
            <dt>上涨 / 下跌</dt>
            <dd>{{ analysis.market.advancers }} / {{ analysis.market.decliners }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="unchanged"
                label="平盘 / 已确认停牌"
              />
            </dt>
            <dd>{{ analysis.market.unchanged }} / {{ analysis.market.suspended }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="coverage"
                label="清单覆盖率"
              />
            </dt>
            <dd>{{ percent(analysis.market.coverage, 2) }}</dd>
          </div>
        </dl>
        <p class="help-text">
          供应商清单 {{ analysis.market.expected }} 只，实际交易
          {{ analysis.market.observed }} 只。该截面只描述当日成交与涨跌，不等于全市场五维评级。
        </p>
        <p
          v-if="analysis.market.status === 'PARTIAL'"
          class="notice notice--subtle"
        >
          当前统计仅包含有证据的记录，未核验部分不计作下跌或停牌，也不补成0；上述金额不是完整市场合计。
        </p>
      </section>
    </template>
    <template v-if="show('stocks')">
      <article
        v-for="stock in visibleStocks"
        :key="stock.symbol"
        class="report-stock-fact"
      >
        <header class="report-stock-fact__header">
          <div>
            <h3>
              {{ stock.name }}
              <small>{{ stock.symbol }}</small>
            </h3>
            <p>
              {{ stock.asOf || '行情日期缺失' }} · {{ sourceName(stock.source) }} ·
              {{ stock.trend }}
            </p>
          </div>
          <span class="report-risk-count">
            {{ stock.triggered }} 项触发 / {{ stock.reviewed }} 项可评估
          </span>
        </header>
        <p
          v-if="stock.status !== 'TRADING'"
          class="notice notice--warning"
        >
          {{
            stock.status === 'SUSPENDED'
              ? '本期停牌，最近价格不代表当日成交。'
              : '业务日期有效行情缺失，收益与趋势不可完整判断。'
          }}
        </p>
        <dl class="report-metric-grid">
          <div>
            <dt>
              <TermHelp
                term="kline"
                label="收盘价"
              />
            </dt>
            <dd>
              {{ number(stock.close) }}
              <small>元</small>
            </dd>
          </div>
          <div>
            <dt>{{ weekly ? '本周' : '当日' }}复权收益</dt>
            <dd>{{ percent(stock.periodReturn, 2) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="return"
                label="5个有K线交易日收益"
              />
            </dt>
            <dd>{{ percent(stock.return5, 2) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="return"
                label="20个有K线交易日收益"
              />
            </dt>
            <dd>{{ percent(stock.return20, 2) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="ma-distance"
                label="距20日均线"
              />
            </dt>
            <dd>{{ percent(stock.distanceMa20, 2) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="ma-distance"
                label="距60日均线"
              />
            </dt>
            <dd>{{ percent(stock.distanceMa60, 2) }}</dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="volume-ratio"
                label="量比"
              />
            </dt>
            <dd>
              {{ number(stock.volumeRatio) }}
              <small>倍</small>
            </dd>
          </div>
          <div>
            <dt>
              <TermHelp
                term="rs20"
                label="20日相对沪深300"
              />
            </dt>
            <dd>{{ percent(stock.rs20, 2) }}</dd>
          </div>
        </dl>
        <details
          v-if="financial(stock.symbol)"
          class="report-followup"
          :open="section !== 'stocks'"
        >
          <summary>累计财报与财务证据</summary>
          <template
            v-for="f in analysis.financials?.filter((item) => item.symbol === stock.symbol)"
            :key="f.symbol"
          >
            <p>
              {{ f.periodEnd }} ·
              {{ f.source === 'EASTMONEY_FREE' ? '东方财富公开财报' : f.source }}
            </p>
            <dl class="report-metric-grid">
              <div>
                <dt>
                  <TermHelp
                    term="revenue"
                    label="累计营业收入"
                  />
                </dt>
                <dd>{{ money(f.revenue) }}</dd>
              </div>
              <div>
                <dt>
                  <TermHelp
                    term="revenue"
                    label="归母净利润"
                  />
                </dt>
                <dd>{{ money(f.netProfit) }}</dd>
              </div>
              <div>
                <dt>
                  <TermHelp
                    term="yoy"
                    label="营收同比 / 利润同比"
                  />
                </dt>
                <dd>{{ percent(f.revenueYoy, 2) }} / {{ percent(f.profitYoy, 2) }}</dd>
              </div>
              <div>
                <dt>
                  <TermHelp
                    term="gross-margin"
                    label="毛利率"
                  />
                </dt>
                <dd>{{ percent(f.grossMargin, 2) }}</dd>
              </div>
              <div>
                <dt>
                  <TermHelp
                    term="cashflow"
                    label="累计经营现金流"
                  />
                </dt>
                <dd>{{ money(f.operatingCashflow) }}</dd>
              </div>
            </dl>
            <p class="help-text">
              公告 {{ f.publishedAt.replace('T', ' ') }} · 本版本可用起点
              {{
                f.availableAt.replace('T', ' ')
              }}。毛利率和现金流规则按上年、前年相同累计期间比较。
            </p>
          </template>
        </details>
        <p>
          <RouterLink
            :to="{ path: `/stocks/${stock.symbol}`, query: reportDate ? { date: reportDate } : {} }"
            class="text-link"
          >
            按本期日期查看持有与买卖计划 →
          </RouterLink>
          <span class="help-text">
            使用当前保存版本回看
            {{ reportDate || '所选日期' }}，可能含后来补录资料；当时的证据以本报告冻结内容为准。
          </span>
        </p>
        <PriceVolumeCard
          v-if="priceVolume(stock.symbol)"
          :study="priceVolume(stock.symbol)!"
        />
        <p
          v-else
          class="help-text"
        >
          本修订未冻结近两月量价研究；生成新报告修订后可查看压力、止损参考与减仓观察意见。
        </p>
        <p class="report-levels">
          本期观测值：MA20 {{ number(stock.ma20) }} 元 · MA60 {{ number(stock.ma60) }} 元 ·
          前20日收盘低点（同锚点复权）{{ number(stock.previous20Low) }} 元
        </p>
        <section class="report-rule-evidence">
          <h4>已触发规则与依据</h4>
          <p
            v-if="!rules(stock.symbol, true).length"
            class="muted"
          >
            本区间没有已触发记录；证据不足的规则列在下方，不能据此认定整体低风险。
          </p>
          <article
            v-for="rule in rules(stock.symbol, true)"
            :key="rule.ruleCode + rule.date"
            class="report-rule-row"
          >
            <div>
              <strong>
                <TermHelp
                  :term="ruleTermIds[rule.ruleCode]"
                  :label="rule.ruleName"
                />
              </strong>
              <small>{{ rule.date }}</small>
              <StateBadge :value="rule.severity" />
            </div>
            <p>
              <span
                v-for="(value, index) in rule.evidence"
                :key="value.label"
              >
                {{ index ? '；' : '' }}{{ value.label }} {{ metric(value) }}
              </span>
            </p>
          </article>
          <details class="report-other-rules">
            <summary>
              其他规则：未触发、证据不足或不适用（{{ rules(stock.symbol, false).length }}项）
            </summary>
            <article
              v-for="rule in rules(stock.symbol, false)"
              :key="rule.ruleCode + rule.date"
              class="report-rule-row"
            >
              <div>
                <strong>
                  <TermHelp
                    :term="ruleTermIds[rule.ruleCode]"
                    :label="rule.ruleName"
                  />
                </strong>
                <small>{{ rule.date }}</small>
                <StateBadge :value="rule.status" />
              </div>
              <p v-if="rule.status === 'NOT_APPLICABLE'">自选研究模式，不评估个人持仓风险。</p>
              <p v-else-if="rule.status === 'INSUFFICIENT_DATA' && rule.explanation">
                {{ rule.explanation }}
              </p>
              <p v-else-if="rule.missingInputs.length">缺少：{{ rule.missingInputs.join('、') }}</p>
              <p v-else>
                <span
                  v-for="(value, index) in rule.evidence"
                  :key="value.label"
                >
                  {{ index ? '；' : '' }}{{ value.label }} {{ metric(value) }}
                </span>
              </p>
              <p
                v-if="rule.explanation && rule.status !== 'INSUFFICIENT_DATA'"
                class="help-text"
              >
                {{ rule.explanation }}
              </p>
            </article>
          </details>
        </section>
        <details
          class="report-secondary-evidence"
          :open="section !== 'stocks'"
        >
          <summary>同业联动、研究建议与后续核对</summary>
          <ThemeLinkageCard
            v-for="theme in analysis.themes?.filter((t) => t.symbol === stock.symbol) || []"
            :key="theme.themeCode"
            :research="theme"
          />
          <ResearchAdviceCard
            v-if="advice(stock.symbol)"
            :advice="advice(stock.symbol)!"
          />
          <section
            v-else
            class="report-followup"
          >
            <h4>后续核对</h4>
            <div
              v-for="check in analysis.checks.filter((c) => c.symbol === stock.symbol)"
              :key="check.title"
            >
              <strong>{{ check.title }}</strong>
              <p>{{ check.detail }}</p>
            </div>
          </section>
        </details>
      </article>
    </template>
    <details
      v-if="show('overview', 'market')"
      class="report-data-gaps"
    >
      <summary>尚未覆盖的数据与方法边界</summary>
      <ul>
        <li
          v-for="item in analysis.missingData"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
      <p
        v-for="note in analysis.methodNotes"
        :key="note"
        class="help-text"
      >
        {{ note }}
      </p>
    </details>
  </div>
</template>
