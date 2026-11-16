<script setup lang="ts">
import AsyncState from '../components/common/AsyncState.vue';
import TermHelp from '../components/common/TermHelp.vue';
import SectionCard from '../components/common/SectionCard.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { ref, watch } from 'vue';
import { getPositions, savePosition, saveAccount } from '../api/portfolio';
import { useMutation } from '../composables/useMutation';
import { number } from '../utils/format';
import type { Position } from '../types/models';
const props = defineProps<PageProps>();
const { data, loading, error, reload } = useResource(getPositions, [() => props.refreshToken]);
const { busy, error: mutationError, run } = useMutation();
const assets = ref<number>();
const symbol = ref('');
const quantity = ref(0);
const cost = ref(0);
const success = ref('');
watch(data, (value) => {
  assets.value =
    value?.account.total_assets == null ? undefined : Number(value.account.total_assets);
});
function edit(position: Position) {
  symbol.value = position.symbol;
  quantity.value = Number(position.quantity);
  cost.value = Number(position.cost_price);
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
        asOfDate: props.date,
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
      await saveAccount(assets.value!, props.date);
      await reload();
    })
  ) {
    success.value = '投资资产总额已保存。';
  }
}
</script>
<template>
  <div class="page-stack">
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
            登记日期：{{ data?.account.as_of_date || '暂无' }} · 本次保存日期：{{ date }}
          </small>
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
          <div class="form-meta">保存业务日期 {{ date }}</div>
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
