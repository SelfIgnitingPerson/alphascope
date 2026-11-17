<script setup lang="ts">
import { api } from '../../api/client';
import { useResource } from '../../composables/useResource';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
const props = defineProps<{ refreshToken: number }>();
type Totals = {
  usedBytes: number;
  reservedBytes: number;
  limitBytes: number;
  databaseAllocatedBytes: number;
};
type Storage = {
  privateBudget: Totals;
  platform: Totals;
  runtimeUsableBytes: number;
  minimumRuntimeFreeBytes: number;
  databaseDiskStatus: string;
};
const { data, loading, error, reload } = useResource(
  (signal) => api<Storage>('/admin/operations/storage', { signal }),
  [() => props.refreshToken],
);
const mib = (bytes: number) => (bytes / 1048576).toFixed(1) + ' MiB';
</script>
<template>
  <SectionCard
    title="存储预算与实际占用"
    description="逻辑正文、活动任务预留、数据库物理分配和应用运行磁盘是四种口径。"
  >
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <template v-if="data">
        <dl class="storage-grid">
          <div>
            <dt>当前账号证据</dt>
            <dd>
              {{ mib(data.privateBudget.usedBytes) }} / {{ mib(data.privateBudget.limitBytes) }}
            </dd>
            <dd>活动任务预留 {{ mib(data.privateBudget.reservedBytes) }}</dd>
          </div>
          <div>
            <dt>平台全部证据</dt>
            <dd>{{ mib(data.platform.usedBytes) }} / {{ mib(data.platform.limitBytes) }}</dd>
            <dd>活动任务预留 {{ mib(data.platform.reservedBytes) }}</dd>
          </div>
          <div>
            <dt>数据库物理分配</dt>
            <dd>{{ mib(data.platform.databaseAllocatedBytes) }}</dd>
            <dd>表及索引估算，不等于逻辑正文</dd>
          </div>
          <div>
            <dt>应用运行资料磁盘剩余</dt>
            <dd>{{ mib(data.runtimeUsableBytes) }}</dd>
            <dd>低于 {{ mib(data.minimumRuntimeFreeBytes) }} 暂停新增</dd>
          </div>
        </dl>
        <p class="help-text">
          数据库所在磁盘剩余空间需由数据库宿主监控，此处不以应用磁盘代替。预算达到上限时拒绝新增，既有研究不删除；任务完成、失败或取消后原子释放预留。
        </p>
      </template>
    </AsyncState>
  </SectionCard>
</template>
<style scoped>
.storage-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.storage-grid dd {
  margin: 6px 0;
  overflow-wrap: anywhere;
}
.storage-grid dt {
  font-weight: 600;
}
@media (max-width: 700px) {
  .storage-grid {
    grid-template-columns: 1fr;
  }
}
</style>
