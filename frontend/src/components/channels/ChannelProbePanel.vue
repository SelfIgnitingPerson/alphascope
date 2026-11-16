<script setup lang="ts">
import { computed, ref, watch, onScopeDispose } from 'vue';
import SectionCard from '../common/SectionCard.vue';
import FailureExplanation from '../common/FailureExplanation.vue';
import { useAuth } from '../../composables/useAuth';
import { useMutation } from '../../composables/useMutation';
import { testProvider } from '../../api/provider-probe';
import type { ProviderStatus } from '../../types/market';
import type {
  ProbeCapability,
  ProviderProbeRequest,
  ProviderProbeResult,
} from '../../types/provider-probe';
import { sourceName, timestamp } from '../../utils/format';
import { validResearchDate } from '../../composables/useResearchDate';
const props = defineProps<{
  channels: ProviderStatus['channels'];
  channel: string;
  date: string;
}>();
const emit = defineEmits<{ 'update:channel': [value: string]; completed: [] }>();
const { user } = useAuth();
const admin = computed(() => user.value?.role === 'ADMIN');
const mutation = useMutation();
const { busy, error } = mutation;
const date = ref(props.date);
const editedDate = ref(false);
const capability = ref<ProbeCapability>('STOCK_DAILY');
const result = ref<ProviderProbeResult>();
const now = ref(Date.now());
const nextSubmissionAt = ref(0);
const timer = setInterval(() => {
  now.value = Date.now();
}, 1000);
onScopeDispose(() => clearInterval(timer));
// 同通道服从60秒冷却，切换通道也要避开全局写接口的一秒限频。
const remaining = computed(() => {
  const sameChannel =
    result.value?.channel === props.channel && result.value.retryAt
      ? Math.max(0, Math.ceil((Date.parse(result.value.retryAt) - now.value) / 1000)) || 0
      : 0;
  return Math.max(sameChannel, Math.ceil((nextSubmissionAt.value - now.value) / 1000), 0);
});

const pending = ref<ProviderProbeRequest>();
const selected = computed(() => props.channels.find((value) => value.channel === props.channel));
const abilities: { code: ProbeCapability; label: string; sample: string }[] = [
  { code: 'TRADING_CALENDAR', label: '交易日历', sample: '所选日期的明确开休市状态' },
  { code: 'STOCK_DAILY', label: '股票日线', sample: '浦发银行 · 600000.SH · 一日原价与同源因子' },
  { code: 'INDEX_DAILY', label: '指数日线', sample: '沪深300 · 000300.SH · 一日价格与量额' },
];
const supported = (code: ProbeCapability) =>
  selected.value?.capabilities?.find((value) => value.capability === code)?.supported;
watch(
  () => props.channel,
  () => {
    capability.value = abilities.find((item) => supported(item.code))?.code || 'STOCK_DAILY';
  },
  { immediate: true },
);
watch(
  () => props.date,
  (value) => {
    if (!editedDate.value) date.value = value;
  },
);
const selectedAbility = computed(() => abilities.find((item) => item.code === capability.value)!);
const statusLabels = {
  SUCCESS: '本项样本核验通过',
  FAILED: '本项样本核验失败',
  SKIPPED: '本次未执行上游核验',
  TIMED_OUT: '本次测试超时',
};
async function submit() {
  if (!admin.value || !selected.value) return;
  if (!validResearchDate(date.value)) {
    error.value = '请选择1990年以来、不晚于上海今天的有效日期。';
    return;
  }
  const input = { channel: props.channel, capability: capability.value, date: date.value };
  await mutation.run(async () => {
    pending.value = input;
    result.value = undefined;
    try {
      result.value = await testProvider(input);
      now.value = Date.now();
      if (['SUCCESS', 'FAILED'].includes(result.value.status)) emit('completed');
    } finally {
      now.value = Date.now();
      nextSubmissionAt.value = now.value + 1000;
      pending.value = undefined;
    }
  });
}
</script>
<template>
  <SectionCard
    title="测试指定通道"
    description="主动请求一个基础能力的一日样本；核验数据内容，避免只凭连接成功误判整个通道可用。"
  >
    <div class="notice notice--subtle">
      每次只测试指定通道，不自动降级、不写入行情、不生成报告。一个样本通过不代表完整历史、其他能力或持续服务可用。
      每通道每60秒最多一次，全局同时一个测试；20秒内未完成返回超时。实际网络请求数可能多于一次，例如需分别取得原价与因子。
    </div>
    <p
      v-if="!admin"
      class="notice notice--warning"
    >
      测试使用所有用户共享的上游请求额度，由管理员执行；你仍可查看通道状态与已有诊断。
    </p>
    <form
      v-else
      class="probe-form"
      @submit.prevent="submit"
    >
      <label class="filter-field">
        行情通道
        <el-select
          :model-value="channel"
          aria-label="被测行情通道"
          :disabled="busy"
          @update:model-value="emit('update:channel', String($event))"
        >
          <el-option
            v-for="item in channels"
            :key="item.channel"
            :label="sourceName(item.channel)"
            :value="item.channel"
          />
        </el-select>
      </label>
      <label class="filter-field">
        测试能力
        <el-select
          v-model="capability"
          aria-label="被测通道能力"
          :disabled="busy"
        >
          <el-option
            v-for="item in abilities"
            :key="item.code"
            :value="item.code"
            :label="`${item.label}${supported(item.code) === false ? '（本适配器不支持）' : ''}`"
            :disabled="supported(item.code) === false"
          />
        </el-select>
      </label>
      <label class="filter-field">
        测试日期
        <el-date-picker
          v-model="date"
          aria-label="通道测试日期"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled="busy"
          @update:model-value="editedDate = true"
        />
      </label>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
        :disabled="!selected || remaining > 0"
        data-testid="test-provider"
      >
        {{ remaining > 0 ? `${remaining}秒后可再试` : '测试通道' }}
      </el-button>
    </form>
    <p
      v-if="admin"
      class="help-text"
    >
      样本：{{
        selectedAbility.sample
      }}。建议选择已完成且有数据的交易日；休市或尚无日线不能直接解释成网络故障。全市场截面与因子覆盖需通过正式同步任务核验。
    </p>
    <p
      v-if="selected?.state === 'ACCESS_RESTRICTED'"
      class="notice notice--warning"
    >
      该通道保留访问保护。点击只会返回本地跳过原因，不连接上游、不清除保护、不重新计算解封时间。
    </p>
    <p
      v-if="busy && pending"
      class="help-text"
      role="status"
    >
      正在测试 {{ sourceName(pending.channel) }} ·
      {{ abilities.find((item) => item.code === pending?.capability)?.label }} ·
      {{ pending.date }}；请等待结果。
    </p>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <section
      v-if="result"
      class="probe-result"
      :class="{ 'probe-result--success': result.status === 'SUCCESS' }"
      role="status"
      data-testid="provider-test-result"
    >
      <header>
        <h3>{{ statusLabels[result.status] }}</h3>
        <span>{{ result.elapsedMs }} 毫秒</span>
      </header>
      <p>
        {{ sourceName(result.channel) }} ·
        {{ abilities.find((item) => item.code === result?.capability)?.label }} · 请求日期
        {{ result.requestedDate }}
      </p>
      <p class="help-text">
        本次开始 {{ timestamp(result.checkedAt) }}；这是测试记录，不改写供应商原始拒绝时间。
      </p>
      <dl
        v-if="result.sample"
        class="probe-sample"
      >
        <div>
          <dt>实际来源</dt>
          <dd>{{ sourceName(result.sample.source) }}</dd>
        </div>
        <div>
          <dt>返回日期</dt>
          <dd>{{ result.sample.dataDate }}</dd>
        </div>
        <div>
          <dt>有效记录</dt>
          <dd>{{ result.sample.records }} 条</dd>
        </div>
        <div v-if="result.sample.symbol">
          <dt>样本代码</dt>
          <dd>{{ result.sample.symbol }}</dd>
        </div>
        <div v-if="result.sample.marketOpen !== null">
          <dt>日历结果</dt>
          <dd>{{ result.sample.marketOpen ? '明确开市' : '明确休市；不表示日线存在' }}</dd>
        </div>
      </dl>
      <FailureExplanation
        v-if="result.diagnostic"
        :diagnostic="result.diagnostic"
        :context="sourceName(result.channel)"
      />
      <p
        v-if="result.retryAt"
        class="help-text"
      >
        本机下一次测试最早时间：{{
          timestamp(result.retryAt)
        }}。这不是供应商解封承诺，访问保护与能力冷却仍单独生效。
      </p>
    </section>
  </SectionCard>
</template>
<style scoped>
.probe-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 16px;
  align-items: end;
  margin-top: 20px;
}
.probe-form .el-select,
.probe-form :deep(.el-date-editor.el-input) {
  width: 100%;
  min-width: 0;
}
.probe-result {
  margin-top: 20px;
  border: 1px solid #e6dccb;
  border-radius: 10px;
  padding: 18px;
  background: #fffbf5;
  overflow-wrap: anywhere;
}
.probe-result--success {
  border-color: #bedcd3;
  background: #f4fbf8;
}
.probe-result header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.probe-result h3 {
  margin: 0;
  font-size: 16px;
}
.probe-result header span {
  font-size: 12px;
  white-space: nowrap;
  color: #738398;
}
.probe-sample {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.probe-sample div {
  min-width: 0;
}
.probe-sample dt {
  font-size: 12px;
  color: #738398;
}
.probe-sample dd {
  margin: 6px 0 0;
}
@media (max-width: 1000px) {
  .probe-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .probe-form,
  .probe-sample {
    grid-template-columns: 1fr;
  }
  .probe-result {
    padding: 14px;
  }
  .probe-result header {
    align-items: flex-start;
  }
}
</style>
