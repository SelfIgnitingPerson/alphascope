<script setup lang="ts">
import ChannelCapabilities from '../components/channels/ChannelCapabilities.vue';
import { computed, ref } from 'vue';
import ChannelProbePanel from '../components/channels/ChannelProbePanel.vue';
import { useAuth } from '../composables/useAuth';
import FailureExplanation from '../components/common/FailureExplanation.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import SectionTabs from '../components/common/SectionTabs.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import type { PageProps } from '../types/models';
import { getProviders, getBaoStockPolicy } from '../api/market';
import { sourceName, timestamp } from '../utils/format';
import { channelStatusExplanation } from '../content/status-explanations';
import {
  capabilityNames,
  channelGuides,
  channelConclusion,
  savedChannelEvidence,
} from '../content/channel-guides';

const props = defineProps<PageProps>();
const { user } = useAuth();
async function refreshStatus() {
  await Promise.allSettled([reload(), baoPolicy.reload()]);
}
const { data, loading, error, reload } = useResource(getProviders, [() => props.refreshToken]);
const baoPolicy = useResource(getBaoStockPolicy, [() => props.refreshToken]);
const tab = ref('status');
const selected = ref('BAOSTOCK');
const tabs = [
  { id: 'status', label: '通道原因与处理' },
  { id: 'test', label: '通道测试' },
  { id: 'capabilities', label: '能力对照' },
  { id: 'saved', label: '已入库数据' },
];
const channel = computed(
  () =>
    data.value?.channels.find((item) => item.channel === selected.value) ?? data.value?.channels[0],
);
const guide = computed(() => (channel.value ? channelGuides[channel.value.channel] : undefined));
const evidence = computed(() =>
  savedChannelEvidence(channel.value?.channel ?? '', data.value?.latestData ?? []),
);
const supported = computed(
  () => channel.value?.capabilities?.filter((item) => item.supported) ?? [],
);
const skipped = computed(
  () => channel.value?.capabilities?.filter((item) => !item.supported) ?? [],
);
const issues = computed(() => {
  const groups = new Map<
    string,
    { names: string[]; diagnostic: NonNullable<NonNullable<typeof channel.value>['diagnostic']> }
  >();
  for (const item of supported.value) {
    if (!item.diagnostic) continue;
    const previous = groups.get(item.diagnostic.code);
    const name = capabilityNames[item.capability] ?? item.capability;
    if (previous) previous.names.push(name);
    else groups.set(item.diagnostic.code, { names: [name], diagnostic: item.diagnostic });
  }
  if (!groups.size && channel.value?.diagnostic) {
    groups.set(channel.value.diagnostic.code, {
      names: [sourceName(channel.value.channel)],
      diagnostic: channel.value.diagnostic,
    });
  }
  return [...groups.values()];
});
const protectedCount = computed(
  () =>
    data.value?.channels.filter(
      (item) =>
        item.state === 'ACCESS_RESTRICTED' ||
        item.capabilities?.some(
          (ability) =>
            ability.supported && ['ACCESS_RESTRICTED', 'ACCESS_COOLDOWN'].includes(ability.state),
        ),
    ).length ?? 0,
);
const newestStored = computed(() =>
  data.value?.latestData
    .map((item) => item.trade_date)
    .sort()
    .at(-1),
);
</script>
<template>
  <AsyncState
    :loading="loading"
    :error="error"
    :has-content="Boolean(data)"
    @retry="reload"
  >
    <template v-if="data">
      <div class="notice notice--subtle">
        <strong>{{ data.isSynthetic ? '演示模式' : '免费优先 · 真实行情' }}</strong>
        <span>
          先看具体能力和实际证据，再判断是否受限。刷新本页只读取状态，不测试连接、不解除保护。
        </span>
      </div>
      <div class="channel-summary">
        <div>
          <span>当前路由</span>
          <strong>{{ data.channels.length }} 个通道</strong>
        </div>
        <div>
          <span>访问保护或持久冷却</span>
          <strong>{{ protectedCount }} 个通道</strong>
        </div>
        <div>
          <span>库中最新行情日</span>
          <strong>{{ newestStored ?? '尚无记录' }}</strong>
        </div>
      </div>
      <SectionTabs
        v-model="tab"
        label="行情通道信息"
        :items="tabs"
      >
        <template #status>
          <SectionCard
            title="逐个查看原因与处理办法"
            description="“不支持”是适配范围，“未请求”是缺少当前观测，“访问保护”才表示保留拒绝记录。"
          >
            <div
              class="channel-selector"
              role="group"
              aria-label="选择行情通道"
            >
              <button
                v-for="item in data.channels"
                :key="item.channel"
                type="button"
                :aria-pressed="channel?.channel === item.channel"
                @click="selected = item.channel"
              >
                {{ sourceName(item.channel) }}
                <span v-if="item.state === 'ACCESS_RESTRICTED'">访问保护</span>
              </button>
            </div>
            <article
              v-if="channel"
              class="channel-detail"
            >
              <header class="channel-detail__header">
                <div>
                  <h2>{{ sourceName(channel.channel) }}</h2>
                  <p>{{ channelConclusion(channel) }}</p>
                </div>
                <el-button
                  v-if="user?.role === 'ADMIN'"
                  @click="tab = 'test'"
                  data-testid="open-provider-test"
                >
                  测试此通道
                </el-button>
              </header>
              <p
                v-if="guide"
                class="help-text"
              >
                {{ guide.scope }}
              </p>
              <div class="channel-detail__columns">
                <section
                  class="channel-evidence"
                  aria-label="实际证据"
                >
                  <h3>实际证据</h3>
                  <dl>
                    <div v-if="channel.channel === 'BAOSTOCK' && baoPolicy.data.value?.accessError">
                      <dt>供应商错误</dt>
                      <dd>
                        <strong>
                          {{ baoPolicy.data.value.accessError.code }} ·
                          {{ baoPolicy.data.value.accessError.meaning }}
                        </strong>
                      </dd>
                    </div>
                    <div
                      v-if="
                        channel.channel === 'BAOSTOCK' && baoPolicy.data.value?.accessObservedAt
                      "
                    >
                      <dt>原始拒绝时间</dt>
                      <dd>{{ timestamp(baoPolicy.data.value.accessObservedAt) }}</dd>
                    </div>
                    <div>
                      <dt>已保存行情</dt>
                      <dd>
                        {{
                          evidence.count
                            ? `${evidence.count}个标的 · 最新${evidence.latestDate}`
                            : '没有该来源的已保存日线证据'
                        }}
                      </dd>
                    </div>
                    <div>
                      <dt>最近入库采集</dt>
                      <dd>{{ timestamp(evidence.latestFetched) }}</dd>
                    </div>
                    <div>
                      <dt>进程最近成功</dt>
                      <dd>{{ timestamp(channel.lastSuccess) }}</dd>
                    </div>
                  </dl>
                  <p class="help-text">
                    入库日期证明当时取得过数据，不能保证此刻连通。进程内状态重启后重新统计；没有失败时间时不猜测。
                  </p>
                  <div
                    v-for="ability in supported"
                    :key="ability.capability"
                    class="channel-ability"
                  >
                    <div>
                      <strong>{{ capabilityNames[ability.capability] }}</strong>
                      <StateBadge
                        :value="ability.state"
                        context="channel"
                        :explanation="
                          channelStatusExplanation({
                            channel: channel.channel,
                            capability: ability,
                          })
                        "
                      />
                    </div>
                    <p
                      v-if="ability.lastFailure || ability.retryAt"
                      class="help-text"
                    >
                      最近失败 {{ timestamp(ability.lastFailure) }} · 冷却截止
                      {{ timestamp(ability.retryAt) }}
                    </p>
                  </div>
                  <p
                    v-if="skipped.length"
                    class="help-text"
                  >
                    正常跳过：{{
                      skipped.map((item) => capabilityNames[item.capability]).join('、')
                    }}。重复请求不会增加适配能力。
                  </p>
                </section>
                <section
                  class="channel-resolution"
                  aria-label="处理步骤"
                >
                  <h3>怎么处理</h3>
                  <p
                    v-if="guide"
                    class="channel-boundary"
                  >
                    {{ guide.boundary }}
                  </p>
                  <ol v-if="guide">
                    <li
                      v-for="step in guide.steps"
                      :key="step"
                    >
                      {{ step }}
                    </li>
                  </ol>
                  <a
                    v-if="guide?.reference"
                    class="text-link"
                    :href="guide.reference.url"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ guide.reference.label }} ↗
                  </a>
                  <RouterLink
                    to="/jobs"
                    class="text-link channel-next"
                  >
                    原因处理后，前往任务中心有限重试 →
                  </RouterLink>
                </section>
              </div>
              <div
                v-if="issues.length"
                class="channel-issues"
              >
                <h3>最近错误的详细解释</h3>
                <FailureExplanation
                  v-for="issue in issues"
                  :key="issue.diagnostic.code"
                  :diagnostic="issue.diagnostic"
                  :context="issue.names.join('、')"
                />
              </div>
              <section
                v-if="channel.channel === 'BAOSTOCK'"
                class="channel-budget"
                aria-label="BaoStock本机保护与预算"
              >
                <h3>本机保护与请求预算</h3>
                <p
                  v-if="baoPolicy.loading.value"
                  class="help-text"
                >
                  正在读取本机预算。
                </p>
                <p
                  v-if="baoPolicy.error.value || baoPolicy.data.value?.error"
                  class="provider-card__error"
                >
                  预算状态读取失败：{{
                    baoPolicy.error.value || baoPolicy.data.value?.error
                  }}。不会按零请求处理。
                </p>
                <template v-if="baoPolicy.data.value && !baoPolicy.data.value.error">
                  <p
                    v-if="baoPolicy.data.value.accessProtected"
                    class="notice"
                  >
                    本机访问保护仍开启：此前收到{{
                      baoPolicy.data.value.accessCode ?? '未记录的拒绝代码'
                    }}。这不是会员权限不足，也不证明当前上游仍受限；需核实官方解封情况后由维护人员恢复，刷新页面不会重试。
                  </p>
                  <p
                    v-else
                    class="help-text"
                  >
                    本机未保持访问保护；实际连通性以最近采集结果为准。
                  </p>
                  <p>
                    原始拒绝时间：
                    <strong>{{ timestamp(baoPolicy.data.value.accessObservedAt) }}</strong>
                    （上海时间；不是本次联网检测）
                  </p>
                  <p>
                    {{ baoPolicy.data.value.date }}（上海自然日）：已预占
                    <strong>{{ baoPolicy.data.value.used }}</strong>
                    次／本机预算 {{ baoPolicy.data.value.limit }} 次，剩余
                    {{ baoPolicy.data.value.remaining }} 次。
                  </p>
                </template>
                <p class="help-text">
                  默认预算4万次，计入登录、分页、失败和重试；同出口IP的其他程序不在本机计数内。本项目共用一个串行会话，重启不重置预算。
                </p>
              </section>
            </article>
          </SectionCard>
          <SectionCard
            v-if="data.inactiveChannels?.length"
            title="未进入当前路由的通道"
            description="配置缺项单独说明，不算供应商受限；不会在查询中读取或展示Token。"
          >
            <div
              v-for="item in data.inactiveChannels"
              :key="item.channel"
              class="inactive-channel"
            >
              <h3>
                {{ sourceName(item.channel) }} ·
                {{ item.state === 'NOT_CONFIGURED' ? '配置未完成' : '未启用' }}
              </h3>
              <p>{{ item.reason }}</p>
              <p class="help-text">{{ item.action }}</p>
              <template v-if="channelGuides[item.channel]">
                <details>
                  <summary>该通道的能力与配置步骤</summary>
                  <p>{{ channelGuides[item.channel]!.boundary }}</p>
                  <ol>
                    <li
                      v-for="step in channelGuides[item.channel]!.steps"
                      :key="step"
                    >
                      {{ step }}
                    </li>
                  </ol>
                  <a
                    v-if="channelGuides[item.channel]!.reference"
                    :href="channelGuides[item.channel]!.reference!.url"
                    class="text-link"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ channelGuides[item.channel]!.reference!.label }} ↗
                  </a>
                </details>
              </template>
            </div>
          </SectionCard>
        </template>
        <template #test>
          <ChannelProbePanel
            :channels="data.channels"
            :channel="channel?.channel || ''"
            :date="date"
            @update:channel="selected = $event"
            @completed="refreshStatus"
          />
        </template>
        <template #capabilities><ChannelCapabilities :data="data" /></template>
        <template #saved>
          <SectionCard
            title="已入库数据来源"
            description="以这里的行情日期与实际来源复核数据，不把通道当前故障等同于历史数据失效。"
          >
            <template #actions>
              <RouterLink
                to="/jobs"
                class="text-link"
              >
                前往补跑 →
              </RouterLink>
            </template>
            <el-table
              :data="data.latestData"
              empty-text="当前模式尚无行情，请先补跑日报"
            >
              <el-table-column
                label="标的"
                min-width="130"
              >
                <template #default="s">
                  <RouterLink
                    :to="`/stocks/${s.row.symbol}`"
                    class="text-link"
                  >
                    {{ s.row.symbol }}
                  </RouterLink>
                </template>
              </el-table-column>
              <el-table-column
                label="实际来源"
                min-width="150"
              >
                <template #default="s">{{ sourceName(s.row.source) }}</template>
              </el-table-column>
              <el-table-column
                prop="trade_date"
                label="行情日期"
                min-width="120"
              />
              <el-table-column
                label="采集时间"
                min-width="180"
              >
                <template #default="s">{{ timestamp(s.row.fetched_at) }}</template>
              </el-table-column>
            </el-table>
          </SectionCard>
        </template>
      </SectionTabs>
      <p class="help-text">
        若旧任务只保存“UNAVAILABLE /
        未分类”，无法补造当时根因。请保留任务编号和失败时间；新请求会按能力记录实际失败时间及可确认的分类。公网使用许可与上游连通性分别核验。
      </p>
    </template>
  </AsyncState>
</template>
<style scoped>
.channel-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}
.channel-summary > div {
  border: 1px solid var(--border, #e1e8ef);
  border-radius: 12px;
  padding: 18px;
  background: white;
}
.channel-summary span {
  display: block;
  font-size: 13px;
  color: #78899d;
  margin-bottom: 10px;
}
.channel-summary strong {
  font-size: 20px;
  color: #243348;
}
.channel-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 24px;
}
.channel-selector button {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 16px;
  border: 1px solid #dce5ed;
  border-radius: 8px;
  background: white;
  color: #52667e;
  cursor: pointer;
  font: inherit;
}
.channel-selector button[aria-pressed='true'] {
  border-color: #245f72;
  color: #245f72;
  background: #edf5f7;
  font-weight: 600;
}
.channel-selector button span {
  font-size: 11px;
  color: #9a6344;
}
.channel-detail__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
h2 {
  font-size: 20px;
  margin: 0 0 10px;
}
h3 {
  font-size: 15px;
  margin: 0 0 14px;
}
p,
li {
  line-height: 1.85;
}
p {
  margin: 10px 0;
}
.channel-detail__header p {
  margin: 0 0 10px;
}
.channel-detail__columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 28px;
  margin-top: 24px;
}
.channel-evidence,
.channel-resolution {
  min-width: 0;
  padding: 20px;
  border: 1px solid #e4ebf1;
  border-radius: 10px;
}
dl {
  margin: 0 0 16px;
}
dl > div {
  display: grid;
  grid-template-columns: 7em minmax(0, 1fr);
  gap: 12px;
  padding: 8px 0;
}
dt {
  font-size: 13px;
  color: #78899d;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.channel-ability {
  padding: 10px 0;
  border-top: 1px solid #edf1f5;
}
.channel-ability > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
.channel-boundary {
  padding: 12px;
  background: #f5f8fb;
  border-radius: 8px;
  font-size: 13px;
}
ol {
  padding-left: 22px;
  margin: 14px 0;
}
li {
  padding-left: 3px;
  margin: 10px 0;
  font-size: 13px;
}
.channel-next {
  display: block;
  margin-top: 18px;
  font-size: 13px;
}
.channel-issues,
.channel-budget {
  border-top: 1px solid #e4ebf1;
  margin-top: 24px;
  padding-top: 22px;
}
.inactive-channel {
  padding: 14px 0;
}
.inactive-channel + .inactive-channel {
  border-top: 1px solid #e4ebf1;
}
.inactive-channel h3 {
  margin-bottom: 8px;
}
details summary {
  cursor: pointer;
  font-size: 13px;
  color: #245f72;
  line-height: 2;
}
@media (max-width: 1000px) {
  .channel-detail__columns {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
@media (max-width: 600px) {
  .channel-summary {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .channel-summary > div {
    padding: 12px 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
  }
  .channel-summary span {
    margin: 0;
  }
  .channel-summary strong {
    font-size: 16px;
  }
  .channel-evidence,
  .channel-resolution {
    padding: 14px;
  }
  .channel-detail__header {
    flex-direction: column;
  }
  .channel-selector button {
    padding: 8px 10px;
    font-size: 13px;
  }
}
</style>
