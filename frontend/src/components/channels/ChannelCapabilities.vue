<script setup lang="ts">
import { computed } from 'vue';
import { channelStatusExplanation } from '../../content/status-explanations';
import SectionCard from '../common/SectionCard.vue';
import StateBadge from '../common/StateBadge.vue';
import { sourceName } from '../../utils/format';
import { capabilityNames } from '../../content/channel-guides';
import type { ProviderStatus } from '../../types/market';
const props = defineProps<{ data: ProviderStatus }>();
const matrix = computed(() =>
  Object.entries(capabilityNames).map(([code, name]) => ({
    code,
    name,
    channels: props.data.channels.map((item) => ({
      code: item.channel,
      value: item.capabilities?.find((ability) => ability.capability === code),
    })),
  })),
);
</script>
<template>
  <SectionCard
    title="按所需能力选择通道"
    description="此表是项目已经实现的能力及本进程最近观测；不等于供应商全部产品能力，也不等于账号已获权限。"
  >
    <div class="capability-matrix">
      <table>
        <thead>
          <tr>
            <th scope="col">能力</th>
            <th
              v-for="item in data.channels"
              :key="item.channel"
              scope="col"
            >
              {{ sourceName(item.channel) }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in matrix"
            :key="row.code"
          >
            <th scope="row">{{ row.name }}</th>
            <td
              v-for="item in row.channels"
              :key="item.code"
            >
              <StateBadge
                v-if="item.value?.supported"
                :value="item.value.state"
                context="channel"
                :explanation="
                  channelStatusExplanation({ channel: item.code, capability: item.value })
                "
              />
              <span
                v-else
                class="help-text"
              >
                正常跳过
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="help-text">
      个股日线已包含同源因子；“截面复权因子”专指全市场研究接口。东方财富指数当前仅支持000300.SH。日线能力用于收盘研究，不是盘中实时Tick。
    </p>
    <p class="help-text">
      交易日历来源：{{
        sourceName(data.calendarSource)
      }}。若本进程尚未获取日历，显示“尚未采集”；已存日历和行情不会因此失效。
    </p>
  </SectionCard>
</template>
<style scoped>
.capability-matrix {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
  font-size: 13px;
}
th,
td {
  text-align: left;
  padding: 14px;
  border-bottom: 1px solid #e5ebf1;
}
th {
  color: #71839b;
  background: #f7f9fc;
}
.normal-skip {
  color: #71839b;
  font-size: 12px;
}
</style>
