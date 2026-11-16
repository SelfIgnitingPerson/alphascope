<script setup lang="ts">
import { computed } from 'vue';
import type { PriceVolumeStudy, PriceVolumeZone } from '../../types/price-volume';
import { number, percent, sourceName } from '../../utils/format';
import StateBadge from '../common/StateBadge.vue';
import TermHelp from '../common/TermHelp.vue';
import PositionStrategyCard from './PositionStrategyCard.vue';
const props = defineProps<{ study: PriceVolumeStudy }>();
const amountBasis = computed(() => props.study.volumeBasis === 'TURNOVER_AMOUNT');
const basis: Record<string, string> = {
  CONFIRMED_SWING_HIGH: '已确认波峰',
  TWO_MONTH_HIGH: '两月前高',
  DAILY_VOLUME_ZONE: '日K近似量能密集区',
  RECENT_SWING_LOW: '最近已确认波谷',
  PRIOR_20_LOW: '前20期最低价',
};
const decisions: Record<string, string> = {
  REDUCE_REVIEW: '可考虑减仓',
  PROFIT_PROTECTION_REVIEW: '可评估分批减仓',
  WATCH_REDUCTION: '关注减仓条件',
  OBSERVE: '继续观察',
  UNAVAILABLE: '暂不判断',
};
const zone = (value: PriceVolumeZone) => `${number(value.lower)}—${number(value.upper)} 元`;
</script>
<template>
  <section class="price-volume-study">
    <header>
      <div>
        <h4>近两个月量价 · 压力与止损参考</h4>
        <p class="help-text">{{ study.windowStart }} → {{ study.asOf }} · 两个自然月</p>
      </div>
      <StateBadge
        :value="study.status"
        context="holding"
        :explanation="{
          subject: study.name + ' · 报告冻结量价研究',
          reason: study.conclusion,
          facts: [
            '报告研究日：' + study.asOf,
            '有效日K：' + study.observedDays + ' / 应有' + study.expectedDays + '日',
          ],
          missing: study.missing,
          action: '按本报告冻结的缺项与窗口阅读；需要其他日期的计划时，到自选股详情选择研究日期。',
        }"
      />
    </header>
    <template v-if="study.status === 'AVAILABLE'">
      <p class="help-text">
        {{ sourceName(study.source || '') }} · 有效日K {{ study.observedDays }}/{{
          study.expectedDays
        }}
        · 实际起点 {{ study.actualStart }} · 截止日收盘 {{ number(study.close) }} 元
      </p>
      <dl class="price-volume-metrics">
        <div>
          <dt>
            <TermHelp
              term="return"
              label="5日复权收益"
            />
          </dt>
          <dd>{{ percent(study.return5, 2) }}</dd>
        </div>
        <div>
          <dt>
            <TermHelp
              :term="amountBasis ? 'turnover-ratio' : 'adjustment'"
              :label="`5日 / 此前20日${amountBasis ? '成交额' : '旧代理量'}比`"
            />
          </dt>
          <dd>{{ number(study.volumeRatio5) }} 倍</dd>
        </div>
        <div>
          <dt>
            <TermHelp
              :term="amountBasis ? 'turnover-ratio' : 'adjustment'"
              :label="`当日 / 前20日${amountBasis ? '成交额' : '旧代理量'}比`"
            />
          </dt>
          <dd>{{ number(study.volumeRatio) }} 倍</dd>
        </div>
        <div>
          <dt>
            <TermHelp
              term="atr-reference"
              label="前14期 ATR"
            />
          </dt>
          <dd>{{ number(study.atr14) }} 元</dd>
        </div>
      </dl>
      <div class="price-volume-levels">
        <section>
          <h5>
            <TermHelp
              term="resistance"
              label="上方压力区间"
            />
          </h5>
          <ul v-if="study.resistances.length">
            <li
              v-for="(item, index) in study.resistances"
              :key="index"
            >
              <strong class="reference-price">{{ zone(item) }}</strong>
              <span>{{ basis[item.basis] || item.basis }}</span>
              <small>
                {{ item.dates.join('、') }}
                <template v-if="item.volumeShare != null">
                  · 窗口{{ amountBasis ? '成交额' : '旧代理量' }}占比
                  {{ percent(item.volumeShare, 2) }}
                </template>
              </small>
            </li>
          </ul>
          <p v-else>已达到或突破此前两月高点，上方缺少本窗口压力样本。</p>
        </section>
        <section>
          <h5>
            <TermHelp
              term="stop-reference"
              label="支撑与止损参考"
            />
          </h5>
          <p v-if="study.support">
            {{
              study.close != null && study.close < study.support.lower ? '原支撑已失守' : '结构支撑'
            }}
            <strong>{{ zone(study.support) }}</strong>
          </p>
          <p
            v-if="study.support"
            class="help-text"
          >
            {{ basis[study.support.basis] }} · {{ study.support.dates.join('、') }}
          </p>
          <p :class="{ 'price-volume-breached': study.stopStatus === 'BREACHED' }">
            止损参考
            <strong class="reference-price">{{ number(study.stopReference) }} 元</strong>
            ·
            {{
              study.stopStatus === 'BREACHED'
                ? '收盘已触及或跌破'
                : study.stopStatus === 'ABOVE_REFERENCE'
                  ? '收盘尚未触及'
                  : '无法形成有效参考'
            }}
          </p>
          <p
            v-if="study.stopReference != null && study.close != null && study.close > 0"
            class="help-text"
          >
            参考位相对本期收盘 {{ percent(study.stopReference / study.close - 1, 2) }}。
            结构参考不等于可承担亏损，应结合个人原有交易计划复核。
          </p>
          <p class="help-text">
            原支撑下沿 − 0.5 ×
            ATR14；本栏按收盘确认，盘中穿越需另行核查。跳空、涨跌停或交易制度可能影响执行。
          </p>
        </section>
      </div>
    </template>
    <div
      class="price-volume-conclusion"
      :class="{ 'price-volume-breached': study.decision === 'REDUCE_REVIEW' }"
    >
      <strong>减仓观察意见 · {{ decisions[study.decision] || '暂不判断' }}</strong>
      <p>{{ study.conclusion }}</p>
    </div>
    <PositionStrategyCard
      v-if="study.strategies?.length"
      :strategies="study.strategies"
    />
    <p
      v-else
      class="help-text"
    >
      本修订只含量价参考；生成新报告修订后可查看条件式建仓、加仓和减仓策略。
    </p>
    <ul
      v-if="study.evidence.length"
      class="price-volume-evidence"
    >
      <li
        v-for="item in study.evidence"
        :key="item"
      >
        {{ item }}
      </li>
    </ul>
    <ul
      v-if="study.missing.length"
      class="price-volume-evidence"
    >
      <li
        v-for="item in study.missing"
        :key="item"
      >
        {{ item }}
      </li>
    </ul>
    <p class="help-text">自选研究：未使用个人成本、持仓比例及风险承受度，不提供减仓数量。</p>
    <details>
      <summary>计算方法与适用范围 · {{ study.version }}</summary>
      <ul class="price-volume-evidence">
        <li
          v-for="item in study.methodNotes"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
    </details>
  </section>
</template>
<style scoped>
.price-volume-study {
  margin: 24px 0;
  padding: 20px;
  border: 1px solid #dce5ed;
  border-radius: 12px;
  background: #f8fafc;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
h4 {
  margin: 0 0 8px;
  font-size: 16px;
}
h5 {
  margin: 0 0 12px;
  font-size: 14px;
}
.price-volume-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}
dt {
  color: #64748b;
  font-size: 12px;
}
dd {
  margin: 8px 0 0;
  font-size: 19px;
  font-weight: 600;
}
.price-volume-levels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin: 20px 0;
}
.price-volume-levels ul {
  padding-left: 18px;
  margin: 0;
}
.price-volume-levels li {
  margin-bottom: 12px;
}
.price-volume-levels span,
.price-volume-levels small {
  display: block;
  margin-top: 4px;
  color: #64748b;
}
.price-volume-levels small {
  overflow-wrap: anywhere;
}
.price-volume-conclusion {
  border-left: 3px solid #7c94aa;
  padding-left: 12px;
  margin: 20px 0 12px;
}
.price-volume-conclusion p {
  margin: 8px 0 0;
  line-height: 1.7;
}
.price-volume-breached {
  color: #b54740;
}
.price-volume-evidence {
  padding-left: 20px;
  line-height: 1.8;
  font-size: 13px;
  overflow-wrap: anywhere;
}
summary {
  cursor: pointer;
  color: #64748b;
  font-size: 12px;
}
@media (max-width: 760px) {
  .price-volume-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .price-volume-levels {
    grid-template-columns: 1fr;
  }
  .price-volume-study {
    padding: 14px;
  }
}
</style>
