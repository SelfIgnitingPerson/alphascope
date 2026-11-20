<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import ValuationPeerFields from './ValuationPeerFields.vue';
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { useAuth } from '../../composables/useAuth';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import {
  getValuationThesis,
  getValuationThesisAtClose,
  saveValuationThesis,
  getCorporateActions,
  saveCorporateAction,
  type ValuationThesis,
  type ValuationShareBasis,
  type ComparablePeer,
  type CorporateAction,
} from '../../api/investment-evidence';
import { number } from '../../utils/format';
const props = defineProps<{ symbol: string; date: string; refreshToken: number }>();
const emit = defineEmits<{ saved: [] }>();
const { user } = useAuth();
const { busy, error: mutationError, run } = useMutation();
const message = ref('');
const identity = computed(() => `${props.symbol}:${props.date}`);
const submitted = ref('');
const form = ref<Partial<ValuationThesis>>({});
const marginPercent = ref<number>();
const minimumProfitGrowthPercent = ref<number>();
const checkNegativeCashflow = ref(false);
const consecutiveReports = ref<number>(2);
const shares = ref<Partial<ValuationShareBasis>>({});
const peers = ref<Partial<ComparablePeer>[]>([]);
const action = ref<Partial<CorporateAction>>({});
const {
  data: savedThesis,
  loading: thesisLoading,
  error: thesisError,
  reload: reloadThesis,
} = useResource((signal) => getValuationThesis(props.symbol, props.date, signal), {
  watch: [() => props.symbol, () => props.date, () => props.refreshToken],
  identity: () => `${props.symbol}:${props.date}`,
});
const {
  data: thesisAtClose,
  error: historicalThesisError,
  reload: reloadThesisAtClose,
} = useResource((signal) => getValuationThesisAtClose(props.symbol, props.date, signal), {
  watch: [() => props.symbol, () => props.date, () => props.refreshToken],
  identity: () => `${props.symbol}:${props.date}`,
});
const {
  data: savedActions,
  loading: actionsLoading,
  error: actionsError,
  reload: reloadActions,
} = useResource((signal) => getCorporateActions(props.symbol, props.date, signal), {
  watch: [() => props.symbol, () => props.date, () => props.refreshToken],
  identity: () => `${props.symbol}:${props.date}`,
});
watch([() => props.symbol, () => props.date], () => {
  for (const key of Object.keys(form.value)) delete form.value[key as keyof ValuationThesis];
  for (const key of Object.keys(action.value)) delete action.value[key as keyof CorporateAction];
  marginPercent.value = undefined;
  minimumProfitGrowthPercent.value = undefined;
  checkNegativeCashflow.value = false;
  consecutiveReports.value = 2;
  shares.value = {};
  peers.value = [];
  message.value = '';
});
const preview = computed(() => {
  if (
    [
      form.value.epsLow,
      form.value.epsHigh,
      form.value.peLow,
      form.value.peHigh,
      marginPercent.value,
    ].some((v) => v == null)
  )
    return null;
  const low = form.value.epsLow! * form.value.peLow!,
    high = form.value.epsHigh! * form.value.peHigh!;
  return { low, high, entry: low * (1 - marginPercent.value! / 100) };
});
function loadSaved() {
  if (!savedThesis.value) return;
  Object.assign(form.value, savedThesis.value);
  marginPercent.value = savedThesis.value.safetyMargin * 100;
  const policy = savedThesis.value.exitPolicy;
  minimumProfitGrowthPercent.value =
    policy?.minimumProfitYoy == null ? undefined : policy.minimumProfitYoy * 100;
  checkNegativeCashflow.value = policy?.checkNegativeCashflow ?? false;
  consecutiveReports.value = policy?.consecutiveReports ?? 2;
  shares.value = { ...(savedThesis.value.shareBasis || {}) };
  peers.value = (savedThesis.value.peers || []).map((v) => ({ ...v }));
}
async function saveThesis() {
  const requestIdentity = identity.value;
  submitted.value = requestIdentity;
  await run(async () => {
    if (
      !preview.value ||
      !form.value.validUntil ||
      !form.value.rationale ||
      !form.value.invalidation ||
      !form.value.financialSource ||
      !form.value.sourceUrl
    )
      throw new Error('请填写EPS、PE、安全边际、复核期限、公开依据和失效条件。');
    const shareValues = [
      shares.value.currentShares,
      shares.value.annualWeightedShares,
      shares.value.parValue,
      shares.value.sourceUrl,
    ];
    const hasShares = shareValues.some((v) => v != null && v !== '');
    if (hasShares && shareValues.some((v) => v == null || v === ''))
      throw new Error('股本核验需同时填写当前股数、年度加权股数、面值和材料链接。');
    if (peers.value.some((p) => !p.symbol || !p.parValue || !p.sourceUrl || !p.rationale))
      throw new Error('每个同行都需代码、面值、材料链接及至少20字可比性说明；不使用的空行请移除。');
    await saveValuationThesis({
      ...form.value,
      symbol: props.symbol,
      asOf: props.date,
      safetyMargin: marginPercent.value! / 100,
      shareBasis: hasShares ? (shares.value as ValuationShareBasis) : null,
      peers: peers.value as ComparablePeer[],
      businessConfirmed: Boolean(form.value.businessConfirmed),
      exitPolicy:
        minimumProfitGrowthPercent.value == null && !checkNegativeCashflow.value
          ? null
          : {
              minimumProfitYoy:
                minimumProfitGrowthPercent.value == null
                  ? null
                  : minimumProfitGrowthPercent.value / 100,
              checkNegativeCashflow: checkNegativeCashflow.value,
              consecutiveReports: consecutiveReports.value,
            },
    } as ValuationThesis);
    if (identity.value !== requestIdentity) return;
    message.value =
      '长期估值情景已保存；回到持有与买卖计划查看条件核对。新情景独立留存，历史报告不会改写。';
    await Promise.all([reloadThesis(), reloadThesisAtClose()]);
    emit('saved');
  });
}
async function saveAction() {
  const requestIdentity = identity.value;
  submitted.value = requestIdentity;
  await run(async () => {
    if (
      [
        action.value.exDate,
        action.value.paymentDate,
        action.value.shareListingDate,
        action.value.priceSource,
        action.value.factorBefore,
        action.value.factorAfter,
        action.value.shareMultiplier,
        action.value.cashPerShare,
        action.value.exReferencePrice,
        action.value.sourceUrl,
      ].some((v) => v == null || v === '')
    )
      throw new Error('请完整填写公告日期、同源因子和行动参数；无送转填1，无现金分红填0。');
    await saveCorporateAction({ ...action.value, symbol: props.symbol } as CorporateAction);
    if (identity.value !== requestIdentity) return;
    message.value = '公司行动证据已登记。请在历史信号中提交新验证；旧冻结结果保留原版本。';
    await reloadActions();
  });
}
</script>
<template>
  <div class="page-stack">
    <el-alert
      v-if="mutationError && submitted === identity"
      :title="mutationError"
      type="error"
      :closable="false"
    />
    <p
      v-if="message"
      role="status"
      class="notice notice--subtle"
    >
      {{ message }}
    </p>
    <SectionCard
      title="我的长期估值情景"
      description="估值=年化每股收益假设（EPS）×市盈率倍数（PE）。输入须有公开依据，情景属于当前用户，不是系统目标价。"
    >
      <AsyncState
        :loading="thesisLoading"
        :error="thesisError"
        @retry="reloadThesis"
      >
        <p
          v-if="thesisAtClose !== undefined || historicalThesisError"
          class="help-text"
        >
          {{
            historicalThesisError
              ? '历史留档核验失败'
              : thesisAtClose
                ? '研究日截止前已有有效情景留档'
                : '研究日截止前缺少有效情景留档'
          }}
        </p>
        <p v-if="savedThesis">
          已保存情景：{{ savedThesis.asOf }}；最迟复核 {{ savedThesis.validUntil }}。
          <el-button @click="loadSaved">载入已有假设</el-button>
        </p>
        <p
          v-else
          class="help-text"
        >
          尚无估值情景时，长期建仓会继续显示等待，不会从均线推断合理价值。
        </p>
      </AsyncState>
      <details class="reading-details">
        <summary>填写或更新我的估值假设</summary>
        <div class="evidence-form">
          <label>
            保守年化EPS（元）
            <el-input-number
              v-model="form.epsLow"
              :min="0.01"
              :max="1000"
              :precision="4"
            />
          </label>
          <label>
            乐观年化EPS（元）
            <el-input-number
              v-model="form.epsHigh"
              :min="0.01"
              :max="1000"
              :precision="4"
            />
          </label>
          <label>
            保守PE（倍）
            <el-input-number
              v-model="form.peLow"
              :min="0.1"
              :max="200"
            />
          </label>
          <label>
            乐观PE（倍）
            <el-input-number
              v-model="form.peHigh"
              :min="0.1"
              :max="200"
            />
          </label>
          <label>
            安全边际（%）
            <el-input-number
              v-model="marginPercent"
              :min="0"
              :max="80"
            />
          </label>
          <label>
            最迟复核日期
            <el-date-picker
              v-model="form.validUntil"
              value-format="YYYY-MM-DD"
              :clearable="false"
            />
          </label>
          <label class="wide">
            盈利、股本口径、PE与业务证据说明
            <el-input
              v-model="form.rationale"
              type="textarea"
              :rows="3"
              maxlength="4000"
              placeholder="说明盈利依据、股本是否调整、行业倍数理由和核心业务证据。"
            />
          </label>
          <label class="wide">
            哪些事实出现后，原假设失效
            <el-input
              v-model="form.invalidation"
              type="textarea"
              :rows="2"
              maxlength="2000"
              placeholder="写出可观察的经营、现金流或产业变化条件。"
            />
          </label>
          <label>
            EPS依据实际来源
            <el-select v-model="form.financialSource">
              <el-option
                v-for="provider in [
                  { code: 'EASTMONEY_FINANCIAL', name: '东方财富财报' },
                  { code: 'BAOSTOCK', name: '证券宝' },
                  { code: 'SINA', name: '新浪财经' },
                  { code: 'TUSHARE', name: 'Tushare' },
                ]"
                :key="provider.code"
                :label="provider.name"
                :value="provider.code"
              />
            </el-select>
          </label>
          <label class="wide">
            公开财报或业务证据链接
            <el-input
              v-model="form.sourceUrl"
              maxlength="1000"
              placeholder="https://…"
            />
          </label>
        </div>
        <p class="help-text">
          安全边际是从估值情景预留的折价，例如情景价值100元、边际20%，观察上限为80元；它不能保证不亏损。
        </p>
        <el-checkbox v-model="form.businessConfirmed">
          我已核对公开资料中的经营逻辑，后续变化会重新复核
        </el-checkbox>
        <p
          v-if="preview"
          class="notice notice--subtle"
        >
          输入情景价值 {{ number(preview.low) }}–{{ number(preview.high) }}
          元；保守情景扣安全边际后的观察上限
          {{ number(preview.entry) }} 元。低于上限仍须核对财报、趋势与风险过滤。
        </p>
        <p class="help-text">
          研究日
          {{
            date
          }}。假设过期后停止用于长期条件；送转、盈利或行业倍数变化须重建。当前登记不能证明历史当时可得。
        </p>
        <details
          class="reading-details valuation-basis"
          open
        >
          <summary>设置可复核的财报退出条件</summary>
          <p class="help-text">
            至少登记一项条件，才能核对长期买入计划。连续达到同一项条件时提示复核退出。
          </p>
          <div class="evidence-form">
            <label>
              归母利润同比下限（%，可留空）
              <el-input-number
                v-model="minimumProfitGrowthPercent"
                :min="-100"
                :max="100"
              />
            </label>
            <label>
              连续报告期数
              <el-input-number
                v-model="consecutiveReports"
                :min="1"
                :max="4"
                :precision="0"
              />
            </label>
            <el-checkbox v-model="checkNegativeCashflow">检查经营现金流连续为负</el-checkbox>
          </div>
        </details>
        <details class="reading-details valuation-basis">
          <summary>补充可交叉核验的股本与同行材料</summary>
          <p class="help-text">
            股数单位为股，不能填成万股；面值应按公开材料填写，不能默认1元。同行价格与利润由服务端读取，不接受手填同行PE。
          </p>
          <div class="evidence-form">
            <label>
              当前总股数
              <el-input-number
                v-model="shares.currentShares"
                :min="0"
                :controls="false"
              />
            </label>
            <label>
              最近年度加权平均股数
              <el-input-number
                v-model="shares.annualWeightedShares"
                :min="0"
                :controls="false"
              />
            </label>
            <label>
              每股面值（元）
              <el-input-number
                v-model="shares.parValue"
                :min="0"
                :controls="false"
                :precision="4"
              />
            </label>
            <label>
              股本与面值材料链接
              <el-input
                v-model="shares.sourceUrl"
                placeholder="https://..."
              />
            </label>
          </div>
          <ValuationPeerFields
            v-for="(_, index) in peers"
            :key="index"
            v-model="peers[index]"
            @remove="peers.splice(index, 1)"
          />
          <el-button
            :disabled="peers.length >= 8"
            @click="peers.push({})"
          >
            增加明确同行（{{ peers.length }}/8）
          </el-button>
          <p class="help-text">
            至少3家有效同行才进行独立上限检查。缺行情、财报或口径不同的同行会被排除；填写材料不等于系统确认业务前景。
          </p>
        </details>
        <el-button
          :loading="busy"
          @click="saveThesis"
        >
          保存我的长期情景
        </el-button>
      </details>
    </SectionCard>
    <SectionCard
      title="公司行动与回放缺项"
      description="持仓跨越分红、送转时，需要公告账务；复权因子只说明价格尺度，不能代替现金到账或新增股份证据。"
    >
      <AsyncState
        :loading="actionsLoading"
        :error="actionsError"
        @retry="reloadActions"
      >
        <p
          v-if="!savedActions?.length"
          class="help-text"
        >
          尚无已登记行动。因子变化时会保留“回放不完整”，不会猜测分红、送转或收益。
        </p>
        <ul v-else>
          <li
            v-for="row in savedActions"
            :key="`${row.exDate}:${row.priceSource}`"
          >
            {{ row.exDate }} · {{ row.priceSource }} · 每原股现金 {{ row.cashPerShare }} 元 ·
            股数倍数 {{ row.shareMultiplier }} · 现金到账 {{ row.paymentDate }}
          </li>
        </ul>
      </AsyncState>
      <details
        v-if="user?.role === 'ADMIN'"
        class="reading-details"
      >
        <summary>根据公开公告登记公司行动</summary>
        <div class="evidence-form">
          <label>
            除权除息日
            <el-date-picker
              v-model="action.exDate"
              value-format="YYYY-MM-DD"
            />
          </label>
          <label>
            现金到账日
            <el-date-picker
              v-model="action.paymentDate"
              value-format="YYYY-MM-DD"
            />
          </label>
          <label>
            新增股份可交易日
            <el-date-picker
              v-model="action.shareListingDate"
              value-format="YYYY-MM-DD"
            />
          </label>
          <label>
            回放行情来源
            <el-select v-model="action.priceSource">
              <el-option
                v-for="code in ['SINA', 'BAOSTOCK', 'EASTMONEY', 'TUSHARE']"
                :key="code"
                :label="code"
                :value="code"
              />
            </el-select>
          </label>
          <label>
            前交易日同源因子
            <el-input-number
              v-model="action.factorBefore"
              :min="0.00000001"
              :precision="8"
            />
          </label>
          <label>
            除权日同源因子
            <el-input-number
              v-model="action.factorAfter"
              :min="0.00000001"
              :precision="8"
            />
          </label>
          <label>
            每原股对应股数（无送转填1）
            <el-input-number
              v-model="action.shareMultiplier"
              :min="1"
              :max="10"
              :precision="4"
            />
          </label>
          <label>
            每原股税前现金（无分红填0）
            <el-input-number
              v-model="action.cashPerShare"
              :min="0"
              :max="1000"
              :precision="4"
            />
          </label>
          <label>
            除权参考价（元）
            <el-input-number
              v-model="action.exReferencePrice"
              :min="0.001"
              :precision="3"
            />
          </label>
          <label class="wide">
            公开公告链接
            <el-input
              v-model="action.sourceUrl"
              maxlength="1000"
              placeholder="https://…"
            />
          </label>
        </div>
        <p class="help-text">
          目前支持现金分红与除权日即可交易的送转。现金到账前不能再投资；模拟按20%预扣税保守计提。不支持配股、未上市新股和零股处置时，继续保留缺项。
        </p>
        <el-button
          :loading="busy"
          @click="saveAction"
        >
          登记公开行动证据
        </el-button>
      </details>
    </SectionCard>
  </div>
</template>
<style scoped>
.evidence-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 18px;
  margin: 20px 0;
}
.evidence-form label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--text-muted);
}
.evidence-form .wide {
  grid-column: 1 / -1;
}
.evidence-form :deep(.el-input-number),
.evidence-form :deep(.el-date-editor) {
  width: 100%;
}
</style>
