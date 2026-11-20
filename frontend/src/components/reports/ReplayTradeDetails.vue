<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ReplayTradeDetails as ReplayDetails } from '../../types/validation';
import { number } from '../../utils/format';

const props = defineProps<{ details: ReplayDetails }>();
const pageSize = 10;
const orderPage = ref(1);
const cyclePage = ref(1);
const orders = computed(() =>
  [...props.details.orders]
    .reverse()
    .slice((orderPage.value - 1) * pageSize, orderPage.value * pageSize),
);
const cycles = computed(() =>
  [...props.details.closedCycles]
    .reverse()
    .slice((cyclePage.value - 1) * pageSize, cyclePage.value * pageSize),
);
</script>

<template>
  <details open>
    <summary>完整平仓盈亏（{{ details.closedCycles.length }} 笔）</summary>
    <el-table
      :data="cycles"
      empty-text="当前区间没有完整平仓"
    >
      <el-table-column
        prop="symbol"
        label="证券"
        min-width="108"
      />
      <el-table-column
        prop="openedOn"
        label="首次建仓"
        min-width="116"
      />
      <el-table-column
        prop="closedOn"
        label="完全平仓"
        min-width="116"
      />
      <el-table-column
        label="扣费后净盈亏（元）"
        min-width="148"
        align="right"
      >
        <template #default="{ row }">{{ number(row.netPnl) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="details.closedCycles.length > pageSize"
      v-model:current-page="cyclePage"
      :page-size="pageSize"
      :total="details.closedCycles.length"
      layout="prev, pager, next"
    />
  </details>
  <details>
    <summary>
      {{ details.entryExecution ? '近期成交、跳过与原因' : '成交、跳过与原因' }}（{{
        details.orders.length
      }}
      条）
    </summary>
    <el-table
      :data="orders"
      empty-text="当前区间没有交易记录"
    >
      <el-table-column
        prop="symbol"
        label="证券"
        min-width="105"
      />
      <el-table-column
        prop="action"
        label="动作"
        min-width="68"
      />
      <el-table-column
        prop="signalDate"
        label="信号日"
        min-width="111"
      />
      <el-table-column
        label="执行尝试日"
        min-width="111"
      >
        <template #default="{ row }">{{ row.executionDate ?? '—' }}</template>
      </el-table-column>
      <el-table-column
        prop="trigger"
        label="触发条件"
        min-width="170"
      />
      <el-table-column
        label="结果"
        min-width="230"
      >
        <template #default="{ row }">
          {{
            row.blockedReason ||
            `成交 ${row.shares} 股 · ${number(row.fillPrice, 4)} 元 · 费用 ${number(row.fee)} 元`
          }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="details.entryExecution"
        label="一手预算（元）"
        min-width="295"
      >
        <template #default="{ row }">
          <template v-if="row.entryBudget">
            <div>
              金额 {{ number(row.entryBudget.oneLotAmount) }} ／仓位额度
              {{ number(row.entryBudget.positionRoom) }}
            </div>
            <div>
              含佣金 {{ number(row.entryBudget.oneLotCost) }} ／可用现金
              {{ number(row.entryBudget.cash) }}
            </div>
            <div>
              估计止损亏损 {{ number(row.entryBudget.estimatedStopLoss) }} ／风险预算
              {{ number(row.entryBudget.riskLimit) }}
            </div>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="details.orders.length > pageSize"
      v-model:current-page="orderPage"
      :page-size="pageSize"
      :total="details.orders.length"
      layout="prev, pager, next"
    />
  </details>
</template>
