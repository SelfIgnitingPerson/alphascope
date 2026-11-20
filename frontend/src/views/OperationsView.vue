<script setup lang="ts">
import LifecyclePanel from '../components/market/LifecyclePanel.vue';
import FullMarketPanel from '../components/market/FullMarketPanel.vue';
import StorageBudgetPanel from '../components/market/StorageBudgetPanel.vue';
import MarketHistoryPanel from '../components/market/MarketHistoryPanel.vue';
import BseUniversePanel from '../components/market/BseUniversePanel.vue';
import { computed, watch, onScopeDispose, ref } from 'vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import { useMutation } from '../composables/useMutation';
import { getOperations, requestBackup } from '../api/operations';
import { timestamp } from '../utils/format';
import type { PageProps } from '../types/models';
const props = defineProps<PageProps>();
const { data, loading, error, reload } = useResource(getOperations, [() => props.refreshToken]);
const { busy, error: actionError, run } = useMutation();
const notice = ref('');
const pending = computed(() =>
  ['QUEUED', 'RUNNING'].includes(data.value?.latestAttempt?.status || ''),
);
let timer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
watch(pending, (value) => {
  clearTimeout(timer);
  if (value) poll();
});
function poll() {
  timer = setTimeout(async () => {
    await reload();
    if (!disposed && pending.value) poll();
  }, 2000);
}
onScopeDispose(() => {
  disposed = true;
  clearTimeout(timer);
});
async function backup() {
  await run(async () => {
    const state = await requestBackup();
    notice.value =
      state === 'QUEUED'
        ? '备份请求已接收，等待实际导出和恢复验证。'
        : '已有备份请求，请等待完成。';
    await reload();
  });
}
const labels: Record<string, string> = {
  HEALTHY: '核查通过',
  ATTENTION: '需要处理',
  FRESH: '日期符合',
  STALE: '已经过期',
  MISSING: '尚无记录',
  INVALID: '证明无效',
  UNKNOWN_CALENDAR: '日历不足',
  VERIFIED: '已核验',
  VERIFIED_SAME_FILESYSTEM: '同盘副本',
  NOT_CONFIGURED: '未配置',
  NOT_CHECKED: '尚未核验',
  QUEUED: '等待执行',
  RUNNING: '正在执行',
  PARTIAL: '部分完成',
  FAILED: '失败',
  INTERRUPTED: '已中断',
};
</script>
<template>
  <FullMarketPanel
    :date="date"
    :refresh-token="refreshToken"
  />
  <StorageBudgetPanel :refresh-token="refreshToken" />
  <LifecyclePanel />
  <BseUniversePanel
    :date="date"
    :refresh-token="refreshToken"
  />
  <MarketHistoryPanel
    :date="date"
    :refresh-token="refreshToken"
  />
  <AsyncState
    :loading="loading"
    :error="error"
    @retry="reload"
  >
    <div
      v-if="data"
      class="page-stack"
      data-testid="operations-overview"
    >
      <SectionCard
        title="运行与数据时效"
        description="服务健康不代表行情已更新；日历不足时保留未知。"
      >
        <template #actions><el-button @click="reload">重新核查</el-button></template>
        <p>
          核查时间：{{ timestamp(data.checkedAt) }} · 应有收盘日期：{{
            data.expectedTradeDate || '尚不能确认'
          }}
        </p>
        <StateBadge
          :value="data.status"
          :label="labels[data.status]"
        />
        <ul
          v-if="data.issues.length"
          class="help-text"
        >
          <li
            v-for="issue in data.issues"
            :key="issue"
          >
            {{ issue }}
          </li>
        </ul>
        <el-table :data="data.instruments">
          <el-table-column
            prop="symbol"
            label="证券"
            min-width="130"
          />
          <el-table-column
            label="日期时效"
            min-width="130"
          >
            <template #default="scope">
              <StateBadge
                :value="scope.row.status"
                :label="labels[scope.row.status]"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="tradeDate"
            label="实际行情日"
            min-width="130"
          />
          <el-table-column
            prop="source"
            label="实际来源"
            min-width="120"
          />
          <el-table-column
            label="采集时刻"
            min-width="180"
          >
            <template #default="scope">{{ timestamp(scope.row.fetchedAt) }}</template>
          </el-table-column>
        </el-table>
      </SectionCard>
      <SectionCard
        title="备份与第二存储"
        description="只有实际恢复验证成功的备份才列为已验证；第二副本另行核验。"
      >
        <template #actions>
          <el-button
            :disabled="busy || pending"
            data-testid="request-backup"
            @click="backup"
          >
            创建并验证备份
          </el-button>
        </template>
        <el-alert
          v-if="actionError"
          :title="actionError"
          type="error"
          :closable="false"
        />
        <p
          v-if="notice"
          role="status"
        >
          {{ notice }}
        </p>
        <p>
          自动备份：{{ data.scheduledBackupEnabled ? '已启用' : '未启用' }} · 上海时区计划：{{
            data.backupCron
          }}
        </p>
        <p>
          本机证明：
          <StateBadge
            :value="data.backup.status"
            :label="labels[data.backup.status]"
          />
          · 最近成功：{{ timestamp(data.backup.verifiedAt) }} · 下次计划：{{
            timestamp(data.backup.nextDue)
          }}
        </p>
        <p
          v-if="data.backup.fileName"
          class="help-text"
        >
          {{ data.backup.fileName }} · SHA256：{{ data.backup.sha256 }}
        </p>
        <p v-if="data.latestAttempt">
          最近尝试：{{ labels[data.latestAttempt.status] || data.latestAttempt.status }} ·
          {{ timestamp(data.latestAttempt.observedAt) }} · {{ data.latestAttempt.reason }}
        </p>
        <p>
          第二存储：
          <StateBadge
            :value="data.secondary.status"
            :label="labels[data.secondary.status]"
          />
          · {{ data.secondary.reason }}
        </p>
      </SectionCard>
    </div>
  </AsyncState>
</template>
