<script setup lang="ts">
import TermHelp from '../common/TermHelp.vue';
import type { ThemeResearch } from '../../types/models';
import StateBadge from '../common/StateBadge.vue';
import { number, percent, timestamp } from '../../utils/format';
defineProps<{ research: ThemeResearch }>();
</script>
<template>
  <section class="report-followup">
    <h4>
      同业联动与分化
      <StateBadge
        :value="research.status"
        :label="research.status === 'AVAILABLE' ? '可计算' : undefined"
      />
    </h4>
    <p>
      <strong>{{ research.title }}</strong>
    </p>
    <p>{{ research.observation }}</p>
    <dl class="report-metric-grid">
      <div>
        <dt>
          <TermHelp
            term="correlation"
            label="20日收益相关系数"
          />
        </dt>
        <dd>{{ number(research.window20.correlation, 3) }}</dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="correlation"
            label="60日收益相关系数"
          />
        </dt>
        <dd>{{ number(research.window60.correlation, 3) }}</dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="correlation"
            label="60日控制市场后相关系数"
          />
        </dt>
        <dd>{{ number(research.window60.marketAdjustedCorrelation, 3) }}</dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="correlation"
            label="同业20日组合收益"
          />
        </dt>
        <dd>{{ percent(research.window20.peerReturn, 2) }}</dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="excess-return"
            label="个股减同业的20日收益差"
          />
        </dt>
        <dd>
          {{
            research.window20.excessReturn == null
              ? '—'
              : number(research.window20.excessReturn * 100) + ' 个百分点'
          }}
        </dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="correlation"
            label="同业60日组合收益"
          />
        </dt>
        <dd>{{ percent(research.window60.peerReturn, 2) }}</dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="excess-return"
            label="个股减同业的60日收益差"
          />
        </dt>
        <dd>
          {{
            research.window60.excessReturn == null
              ? '—'
              : number(research.window60.excessReturn * 100) + ' 个百分点'
          }}
        </dd>
      </div>
      <div>
        <dt>
          <TermHelp
            term="coverage"
            label="同业数据覆盖（20日 / 60日）"
          />
        </dt>
        <dd>
          20日 {{ research.window20.peers.length }}/{{ research.window20.expectedPeers }}；60日
          {{ research.window60.peers.length }}/{{ research.window60.expectedPeers }}
        </dd>
      </div>
    </dl>
    <p class="help-text">
      同业组合已排除 {{ research.symbol }} 自身。相关系数范围为 −1 至
      1；控制市场后相关系数只剔除沪深300的线性影响。
    </p>
    <details>
      <summary>研究样本、近三日广度与原始披露来源</summary>
      <ul>
        <li
          v-for="member in research.members"
          :key="member.symbol"
        >
          {{ member.name }} · {{ member.symbol }}
        </li>
      </ul>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>样本收益</th>
              <th>高于MA20比例</th>
              <th>有效成员</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="day in research.recentDays"
              :key="day.date"
            >
              <td>{{ day.date }}</td>
              <td>{{ percent(day.dayReturn, 2) }}</td>
              <td>{{ percent(day.breadth, 2) }}</td>
              <td>{{ day.validMembers }}/{{ day.totalMembers }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        <a
          :href="research.source.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ research.source.title }}
        </a>
        · {{ research.source.pages }}
      </p>
      <p class="help-text">
        披露版本最早可用时间：{{ timestamp(research.source.availableAt) }}。{{
          research.source.definition
        }}
      </p>
    </details>
    <details>
      <summary>“量子纠缠”类比的使用边界</summary>
      <p>
        这里把“联动、分化”作为研究类比，实际展示经典收益统计；不声称测得股票之间的物理量子纠缠，也不据此预测次日涨跌。
      </p>
      <ul>
        <li
          v-for="item in research.limitations"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
      <a
        href="https://www.nobelprize.org/prizes/physics/2022/press-release/"
        target="_blank"
        rel="noopener noreferrer"
      >
        物理概念来源：2022年诺贝尔物理学奖说明
      </a>
    </details>
  </section>
</template>
<style scoped>
.table-scroll {
  overflow-x: auto;
  margin: 16px 0;
}
table {
  width: 100%;
  border-collapse: collapse;
  text-align: right;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
th,
td {
  padding: 10px 14px;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}
th {
  color: #64748b;
  font-weight: 600;
  background: #f8fafc;
}
th:first-child,
td:first-child {
  text-align: left;
}
</style>
