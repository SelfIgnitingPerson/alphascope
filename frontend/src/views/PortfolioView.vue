<script setup lang="ts">
import AsyncState from '../components/common/AsyncState.vue';
import TermHelp from '../components/common/TermHelp.vue';
import SectionCard from '../components/common/SectionCard.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { ref, watch } from 'vue';
import { today } from '../utils/date';
import { getPositions, savePosition, saveAccount } from '../api/portfolio';
import { useMutation } from '../composables/useMutation';
import { number } from '../utils/format';
import type { Position } from '../types/models';
const props = defineProps<PageProps>();
const historyMode = ref(false);
const registrationDate = ref(today());
const { data, loading, error, reload } = useResource(
  (signal) => getPositions(signal, historyMode.value ? props.date : undefined),
  {
    watch: [() => props.date, () => props.refreshToken, historyMode],
    identity: () => `${props.date}:${historyMode.value}`,
  },
);
const { busy, error: mutationError, run } = useMutation();
const assets = ref<number>();
const riskBudget = ref<number>();
const liquidityReserve = ref<number>();
const fundsNeededOn = ref<string>();
const fundsPurpose = ref('');
const cashBalance = ref<number>();
const positionsComplete = ref(false);
const riskGroup = ref('');
const symbol = ref('');
const quantity = ref(0);
const cost = ref(0);
const success = ref('');
watch(data, (value) => {
  riskBudget.value =
    value?.account.risk_budget == null ? undefined : Number(value.account.risk_budget);
  liquidityReserve.value =
    value?.account.liquidity_reserve == null ? undefined : Number(value.account.liquidity_reserve);
  fundsNeededOn.value = value?.account.funds_needed_on || undefined;
  cashBalance.value =
    value?.account.cash_balance == null ? undefined : Number(value.account.cash_balance);
  positionsComplete.value = Boolean(value?.account.positions_complete);
  fundsPurpose.value = value?.account.funds_purpose || '';
  assets.value =
    value?.account.total_assets == null ? undefined : Number(value.account.total_assets);
});
function edit(position: Position) {
  symbol.value = position.symbol;
  quantity.value = Number(position.quantity);
  cost.value = Number(position.cost_price);
  riskGroup.value = position.risk_group || '';
  success.value = '';
}
async function submitPosition() {
  success.value = '';
  if (!/^\d{6}\.(SH|SZ|BJ)$/.test(symbol.value.trim())) {
    mutationError.value = '请输入完整股票代码。';
    return;
  }
  if (
    await run(async () => {
      await savePosition(symbol.value.trim(), {
        quantity: quantity.value,
        costPrice: cost.value,
        riskGroup: riskGroup.value,
        asOfDate: registrationDate.value,
      });
      await reload();
    })
  ) {
    success.value = '持仓已保存。';
  }
}
async function submitAccount() {
  success.value = '';
  if (!assets.value || assets.value <= 0) {
    mutationError.value = '请填写大于0的投资资产总额。';
    return;
  }
  if (
    await run(async () => {
      await saveAccount({
        totalAssets: assets.value!,
        asOfDate: registrationDate.value,
        riskBudget: riskBudget.value,
        liquidityReserve: liquidityReserve.value,
        fundsNeededOn: fundsNeededOn.value || null,
        fundsPurpose: fundsPurpose.value,
        cashBalance: cashBalance.value,
        positionsComplete: positionsComplete.value,
      });
      await reload();
    })
  ) {
    success.value = '投资资产总额已保存。';
  }
}
</script>
<template>
  <div class="page-stack">
    <div class="inline-form">
      <el-checkbox v-model="historyMode">查看研究日 {{ date }} 当时已实际登记的资料</el-checkbox>
      <label>
        本次登记业务日
        <el-date-picker
          v-model="registrationDate"
          type="date"
          value-format="YYYY-MM-DD"
        />
      </label>
    </div>
    <p class="help-text">
      默认展示当前登记，便于维护；历史模式排除后来补填的资料。盘前可用当前登记按最近收盘估算，但不会冒充历史账户。
    </p>
    <div class="notice notice--subtle">
      <strong>手工资产记录</strong>
      <span>
        持仓与总资产日期一致时才计算
        <TermHelp
          term="concentration"
          label="集中度"
        />
        ；未知值不会按零处理。此页面不提供下单功能。
      </span>
    </div>
    <el-alert
      v-if="mutationError"
      :title="mutationError"
      type="error"
      :closable="false"
    />
    <el-alert
      v-if="success"
      :title="success"
      type="success"
      :closable="false"
    />
    <div class="portfolio-grid">
      <SectionCard title="当前持仓">
        <AsyncState
          :loading="loading"
          :error="error"
          @retry="reload"
        >
          <el-table
            :data="data?.items || []"
            empty-text="尚未登记持仓"
          >
            <el-table-column
              prop="name"
              label="名称"
              min-width="100"
            />
            <el-table-column
              prop="symbol"
              label="代码"
              min-width="120"
            />
            <el-table-column
              label="股数"
              min-width="110"
            >
              <template #default="s">{{ number(s.row.quantity, 0) }}</template>
            </el-table-column>
            <el-table-column
              label="成本价"
              min-width="110"
            >
              <template #default="s">{{ number(s.row.cost_price) }}</template>
            </el-table-column>
            <el-table-column
              prop="as_of_date"
              label="登记日期"
              min-width="110"
            />
            <el-table-column label="操作">
              <template #default="s">
                <el-button
                  link
                  @click="edit(s.row)"
                >
                  编辑
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </AsyncState>
        <form
          class="account-form"
          @submit.prevent="submitAccount"
        >
          <label>投资资产总额（元）</label>
          <div class="inline-form">
            <el-input-number
              v-model="assets"
              :min="1"
              :controls="false"
              placeholder="尚未登记"
            />
            <el-button
              :loading="busy"
              native-type="submit"
            >
              保存总资产
            </el-button>
          </div>
          <small>
            登记日期：{{ data?.account.as_of_date || '暂无' }} · 本次保存日期：{{
              registrationDate
            }}
          </small>
          <details class="reading-details">
            <summary>补充我的用款前提与组合风险预算</summary>
            <p class="help-text">
              预算由你设定，未知就留空。系统不会据此生成仓位比例；这些前提会用于首页个人影响核对。
            </p>
            <div class="personal-context">
              <label>
                现金余额（元）
                <el-input-number
                  v-model="cashBalance"
                  :min="0"
                  :controls="false"
                  placeholder="未知留空"
                />
              </label>
              <label>
                持仓登记完整性
                <el-checkbox v-model="positionsComplete">我确认已登记全部持仓</el-checkbox>
              </label>
              <label>
                组合风险预算（元）
                <el-input-number
                  v-model="riskBudget"
                  :min="0"
                  :controls="false"
                  placeholder="未知留空"
                />
              </label>
              <label>
                近期用款预留（元）
                <el-input-number
                  v-model="liquidityReserve"
                  :min="0"
                  :controls="false"
                  placeholder="未知留空"
                />
              </label>
              <label>
                预计用款日
                <el-date-picker
                  v-model="fundsNeededOn"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="尚未确定"
                />
              </label>
              <label>
                资金用途
                <el-input
                  v-model="fundsPurpose"
                  :maxlength="300"
                  placeholder="如长期闲置资金或计划用款"
                />
              </label>
            </div>
            <p class="help-text">填写后点击上方“保存总资产”，与本次登记日期一同保存。</p>
          </details>
        </form>
      </SectionCard>
      <SectionCard
        title="录入 / 修改持仓"
        description="选择左侧持仓可带入已有数据。"
      >
        <el-form
          label-position="top"
          @submit.prevent="submitPosition"
        >
          <el-form-item label="股票代码">
            <el-input
              v-model="symbol"
              placeholder="如 300476.SZ"
            />
          </el-form-item>
          <el-form-item label="持仓数量（股）">
            <el-input-number
              v-model="quantity"
              :min="0"
              :controls="false"
            />
          </el-form-item>
          <el-form-item label="成本价（元）">
            <el-input-number
              v-model="cost"
              :min="0"
              :precision="4"
              :controls="false"
            />
          </el-form-item>
          <el-form-item label="人工相关风险分组">
            <el-input
              v-model="riskGroup"
              :maxlength="256"
              placeholder="如PCB,AI算力；逗号分隔，可重叠"
            />
          </el-form-item>
          <p class="help-text">
            每次保存会留下不可变登记历史。回看过去只读取当时已经实际登记的资料；补填旧日期不会冒充过去已知。
          </p>
          <div class="form-meta">保存业务日期 {{ registrationDate }}</div>
          <el-button
            type="primary"
            :loading="busy"
            native-type="submit"
            class="full-width"
          >
            保存持仓
          </el-button>
        </el-form>
      </SectionCard>
    </div>
  </div>
</template>

<style scoped>
.personal-context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 16px 0;
}
.personal-context label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}
.personal-context :deep(.el-input-number),
.personal-context :deep(.el-date-editor) {
  width: 100%;
}
@media (max-width: 700px) {
  .personal-context {
    grid-template-columns: 1fr;
  }
}
</style>
