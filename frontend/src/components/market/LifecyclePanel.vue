<script setup lang="ts">
import { ref } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import { api, write } from '../../api/client';
import { useMutation } from '../../composables/useMutation';
const symbol = ref('');
const listedAt = ref('');
const delistedAt = ref('');
const evidenceUrl = ref('');
const reference = ref('');
const notice = ref('');
const { busy, error, run } = useMutation();
async function inspect() {
  await run(async () => {
    const row = await api<{ name: string; listedAt?: string; delistedAt?: string }>(
      `/instruments/${encodeURIComponent(symbol.value.trim().toUpperCase())}`,
    );
    listedAt.value = row.listedAt || '';
    delistedAt.value = row.delistedAt || '';
    notice.value = `${row.name}：${row.listedAt ? '已有核验上市日' : '尚缺上市日，采集会先尝试自动核验'}`;
  });
}
async function save() {
  await run(async () => {
    await api(
      `/admin/instruments/${encodeURIComponent(symbol.value.trim().toUpperCase())}/lifecycle`,
      write('PUT', {
        listedAt: listedAt.value,
        delistedAt: delistedAt.value || null,
        evidenceUrl: evidenceUrl.value,
        reference: reference.value,
      }),
    );
    notice.value = '已保存公开依据；历史报告保持原样。可在任务中心重新发起所需日期的采集。';
  });
}
</script>
<template>
  <SectionCard
    title="证券上市与退市日期"
    description="采集自动读取公开基本资料；自动核验失败时，管理员可依据上市公告补录。北交所旧精选层挂牌日不能充当上市日。"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <el-form
      label-position="top"
      class="filter-form"
    >
      <el-form-item label="证券代码">
        <el-input
          v-model="symbol"
          placeholder="例如 920000.BJ"
        />
      </el-form-item>
      <el-form-item label="已有日期">
        <el-button
          :loading="busy"
          :disabled="!symbol"
          @click="inspect"
        >
          查询登记
        </el-button>
      </el-form-item>
      <el-form-item label="上市日期">
        <el-date-picker
          v-model="listedAt"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="公告确认的上市日"
        />
      </el-form-item>
      <el-form-item label="退市日期（可空）">
        <el-date-picker
          v-model="delistedAt"
          type="date"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="公开公告链接">
        <el-input
          v-model="evidenceUrl"
          placeholder="https://"
        />
      </el-form-item>
      <el-form-item label="公告定位说明">
        <el-input
          v-model="reference"
          placeholder="公告标题、日期和页码"
        />
      </el-form-item>
      <el-form-item label="保存依据">
        <el-button
          type="primary"
          :loading="busy"
          :disabled="!symbol || !listedAt || !evidenceUrl || !reference"
          @click="save"
        >
          保存核验日期
        </el-button>
      </el-form-item>
    </el-form>
  </SectionCard>
</template>
