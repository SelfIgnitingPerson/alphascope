<script setup lang="ts">
import { readableJobError } from '../utils/job-errors';
import { qualityStatusExplanation } from '../content/status-explanations';
import FailureExplanation from '../components/common/FailureExplanation.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import { useMutation } from '../composables/useMutation';
import type { PageProps, JobDetail, ReportType, PageResponse } from '../types/models';
import { ref, computed, watch, onScopeDispose } from 'vue';
import { getJobs, getJob, runJob, retryJob, cancelJob, getJobAttempts } from '../api/jobs';
import { getProviders } from '../api/market';
import { stageNames, timestamp } from '../utils/format';
const props = defineProps<PageProps>();
const jobLabels: Record<string, string> = {
  DAILY: '收盘日报',
  WEEKLY: '每周周报',
  MARKET_HISTORY: '市场历史补齐',
  MARKET_UNIVERSE: '北交所目录补齐',
  MARKET_FULL: '三所全A证据核验',
};
const taskType = ref('');
const taskTypes = [
  { id: '', label: '全部任务' },
  ...Object.entries(jobLabels).map(([id, label]) => ({ id, label })),
];
watch(taskType, () => {
  page.value = 1;
});
const page = ref(1),
  size = ref(20),
  force = ref(false);
const { data, loading, error, reload } = useResource(
  (signal) =>
    getJobs({ page: page.value, size: size.value, type: taskType.value || undefined }, signal),
  {
    watch: [page, taskType, () => props.refreshToken],
    identity: () => taskType.value + ':' + page.value,
  },
);
const { data: providerStatus } = useResource(getProviders, [() => props.refreshToken]);
const currentScope = computed(() => providerStatus.value?.dataScope);
const isHistoricalScope = (scope: string) =>
  Boolean(currentScope.value && scope !== currentScope.value);
const { busy, error: mutationError, run } = useMutation();
const selected = ref<JobDetail>(),
  drawer = ref(false),
  notice = ref('');
const attempts = ref<PageResponse<JobDetail>>();
watch(
  () => selected.value?.id,
  () => {
    attempts.value = undefined;
  },
);
async function loadAttempts(page = 1) {
  const id = selected.value?.id;
  if (!id) return;
  await run(async () => {
    const result = await getJobAttempts(id, page);
    if (selected.value?.id === id) attempts.value = result;
  });
}
const active = (status: string) =>
  ['PENDING', 'RUNNING', 'CANCEL_REQUESTED', 'TIMEOUT_REQUESTED'].includes(status);
/** 数据质量的专用文案，避免与执行成功、市场UNKNOWN混淆。 */
const qualityLabel = (value?: string | null) =>
  !value
    ? '质量未记录'
    : value === 'AVAILABLE'
      ? '数据可用'
      : value === 'UNKNOWN'
        ? '未核验'
        : undefined;
const running = computed(() => data.value?.items.filter((j) => active(j.status)).length || 0);
let poll: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
let detailRequest: AbortController | undefined;
async function refresh() {
  await reload();
  if (disposed) {
    return;
  }
  if (selected.value && drawer.value) {
    detailRequest?.abort();
    const request = new AbortController();
    detailRequest = request;
    const id = selected.value.id;
    try {
      const result = await getJob(id, request.signal);
      if (disposed || request.signal.aborted || selected.value?.id !== id) {
        return;
      }
      selected.value = result;
      if (result.status === 'SUCCESS') {
        notice.value =
          result.job_name === 'MARKET_FULL'
            ? '三所核验已执行，请查看覆盖缺项；本任务不生成报告。'
            : result.job_name === 'MARKET_UNIVERSE'
              ? '北交所在市目录已归档；请核对成员和来源，行情与历史窗口另行验收。'
              : result.job_name === 'MARKET_HISTORY'
                ? '历史补齐已执行，请核对市场覆盖率；本任务不生成报告。'
                : result.data_status === 'PARTIAL' || result.data_status === 'UNAVAILABLE'
                  ? '任务执行完成，报告已归档；研究数据仍有缺项，请查看数据质量。'
                  : '任务已完成，报告已归档。';
      } else if (['FAILED', 'INTERRUPTED', 'CANCELLED', 'TIMED_OUT'].includes(result.status)) {
        notice.value = '任务未完成，请查看执行阶段后重试。';
      }
    } catch (e) {
      if (!request.signal.aborted && !disposed) {
        notice.value = e instanceof Error ? e.message : String(e);
      }
    }
  }
}
function schedule() {
  clearTimeout(poll);
  if (disposed) {
    return;
  }
  poll = setTimeout(async () => {
    await refresh();
    if (!disposed && (running.value > 0 || (selected.value && active(selected.value.status)))) {
      schedule();
    }
  }, 2000);
}
watch(running, (value) => {
  if (value > 0) {
    schedule();
  }
});
async function submit(type: ReportType) {
  await run(async () => {
    selected.value = await runJob({ type, date: props.date, force: force.value });
    drawer.value = true;
    notice.value = active(selected.value.status)
      ? '任务已接收，可离开页面，后台会继续执行。'
      : '复用了输入证据相同的已有结果。';
    await reload();
    schedule();
  });
}
async function retry(id: number) {
  await run(async () => {
    selected.value = await retryJob(id);
    attempts.value = undefined;
    notice.value = '已在原任务编号下重试，旧错误和阶段保存在执行历史；成功后主列表显示最新状态。';
    drawer.value = true;
    await reload();
    schedule();
  });
}
async function cancel(id: number) {
  await run(async () => {
    selected.value = await cancelJob(id);
    drawer.value = true;
    notice.value =
      selected.value.status === 'CANCELLED'
        ? '任务已取消。'
        : selected.value.status === 'CANCEL_REQUESTED'
          ? '已请求取消，后台正在释放资源并保存状态。'
          : '任务已经结束或正在超时收尾，保留当前结果。';
    await reload();
    if (active(selected.value.status)) schedule();
  });
}
async function inspect(id: number) {
  await run(async () => {
    selected.value = await getJob(id);
    drawer.value = true;
    if (active(selected.value.status)) {
      schedule();
    }
  });
}
onScopeDispose(() => {
  disposed = true;
  clearTimeout(poll);
  detailRequest?.abort();
});
</script>
<template>
  <div class="page-stack">
    <SectionCard
      title="按业务日期补跑"
      description="首次回填可能需要数分钟；执行中可在下方查看任务状态。真实当日收盘报告最早15:30后提交，仍需等待供应商发布；盘前请在页头选择前一已完成交易日。"
    >
      <div class="job-toolbar">
        <div>
          <strong>{{ date }}</strong>
          <el-checkbox v-model="force">重新生成新修订</el-checkbox>
          <span>当前业务日期 · {{ running }} 项进行中</span>
        </div>
        <div class="button-row">
          <el-button
            type="primary"
            :loading="busy"
            data-testid="submit-daily"
            @click="submit('DAILY')"
          >
            生成 / 补跑日报
          </el-button>
          <el-button
            :disabled="busy"
            @click="submit('WEEKLY')"
          >
            生成 / 补跑周报
          </el-button>
        </div>
      </div>
      <el-alert
        v-if="mutationError"
        :title="mutationError"
        type="error"
        :closable="false"
        class="form-error"
      />
      <p
        v-if="notice"
        class="help-text"
        role="status"
      >
        {{ notice }}
      </p>
    </SectionCard>
    <SectionCard
      title="执行记录"
      description="按任务类型查看。失败重试沿用原编号；历次原因和阶段收进执行历史。明确生成报告新修订时才建立新任务。"
    >
      <template #actions><el-button @click="refresh">刷新记录</el-button></template>
      <el-radio-group
        v-model="taskType"
        aria-label="按任务类型筛选"
        class="job-type-filter"
      >
        <el-radio-button
          v-for="item in taskTypes"
          :key="item.id"
          :value="item.id"
        >
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
      <AsyncState
        :loading="loading"
        :error="error"
        @retry="reload"
      >
        <el-table
          :data="data?.items || []"
          empty-text="尚无执行记录"
        >
          <el-table-column
            prop="business_date"
            label="业务日期"
            width="120"
          />
          <el-table-column label="任务">
            <template #default="s">
              {{ jobLabels[s.row.job_name] || '其他任务' }}
            </template>
          </el-table-column>
          <el-table-column
            label="运行模式"
            min-width="125"
          >
            <template #default="s">
              <span
                class="scope-label"
                :title="s.row.data_scope"
              >
                {{
                  s.row.data_scope?.startsWith('REAL:')
                    ? '真实行情'
                    : s.row.data_scope === 'DEMO'
                      ? '演示模式'
                      : '早期记录'
                }}
              </span>
              <small
                v-if="isHistoricalScope(s.row.data_scope)"
                class="help-text"
              >
                历史配置
              </small>
            </template>
          </el-table-column>
          <el-table-column
            label="执行状态"
            width="100"
          >
            <template #default="s"><StateBadge :value="s.row.status" /></template>
          </el-table-column>
          <el-table-column
            label="数据质量"
            min-width="105"
          >
            <template #default="s">
              <StateBadge
                :value="s.row.data_status"
                :label="qualityLabel(s.row.data_status)"
                context="quality"
                :explanation="qualityStatusExplanation({ job: s.row })"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="最近结果"
            min-width="230"
          >
            <template #default="s">
              <span
                class="job-latest-result"
                :title="s.row.error || ''"
              >
                {{
                  s.row.error
                    ? readableJobError(s.row.error).summary
                    : active(s.row.status)
                      ? '等待或执行中，尚未形成最终结果'
                      : s.row.status === 'SUCCESS'
                        ? '执行完成；研究范围见数据质量'
                        : '本次未记录失败原因，请查看阶段'
                }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            prop="attempt"
            label="执行序号"
            width="70"
          />
          <el-table-column
            label="操作"
            width="190"
            align="right"
          >
            <template #default="s">
              <el-button
                v-if="['FAILED', 'INTERRUPTED', 'CANCELLED', 'TIMED_OUT'].includes(s.row.status)"
                link
                :disabled="busy"
                :data-testid="'retry-job-' + s.row.id"
                @click="retry(s.row.id)"
              >
                重试
              </el-button>
              <el-button
                v-if="['PENDING', 'RUNNING'].includes(s.row.status)"
                link
                :disabled="busy"
                :data-testid="'cancel-job-' + s.row.id"
                @click="cancel(s.row.id)"
              >
                取消
              </el-button>
              <el-button
                link
                :disabled="busy"
                @click="inspect(s.row.id)"
              >
                查看阶段 →
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="data?.total || 0"
          layout="total, prev, pager, next"
        />
      </AsyncState>
    </SectionCard>
    <el-drawer
      v-model="drawer"
      title="任务执行详情"
      size="min(620px,100%)"
    >
      <template v-if="selected">
        <div
          v-if="isHistoricalScope(selected.data_scope)"
          class="notice notice--subtle"
          role="note"
        >
          <strong>历史配置下的执行记录</strong>
          <span>
            下面的错误仅代表当次执行结果，不代表当前通道状态。当前配置：{{
              currentScope
            }}；本次配置：{{ selected.data_scope }}。
          </span>
        </div>
        <div class="drawer-summary">
          <strong>
            {{ selected.business_date }} ·
            {{ jobLabels[selected.job_name] || '其他任务' }}
          </strong>
          <StateBadge :value="selected.status" />
          <StateBadge
            :value="selected.data_status"
            :label="qualityLabel(selected.data_status)"
            context="quality"
            :explanation="qualityStatusExplanation({ job: selected })"
          />
        </div>
        <p class="help-text">
          任务 #{{ selected.id }} · 本次执行序号 {{ selected.attempt }} · 最近心跳
          {{ timestamp(selected.heartbeat_at) }}
          <span v-if="selected.retry_of">· 重试自 #{{ selected.retry_of }}</span>
        </p>
        <el-button
          :disabled="busy"
          @click="loadAttempts()"
        >
          查看历次执行
        </el-button>
        <section
          v-if="attempts"
          class="attempt-history"
          aria-label="历次执行"
        >
          <p class="help-text">
            当前任务 #{{ selected.id }} · 已归档
            {{ attempts.total }} 次执行；下面是历史结果，不代表本次状态。
          </p>
          <details
            v-for="past in attempts.items"
            :key="past.id + ':' + past.attempt"
            class="attempt-history__item"
          >
            <summary>
              执行序号 {{ past.attempt }} · {{ timestamp(past.ended_at) }} ·
              {{
                past.status === 'SUCCESS'
                  ? '已完成'
                  : past.status === 'FAILED'
                    ? '失败'
                    : past.status === 'INTERRUPTED'
                      ? '已中断'
                      : past.status === 'CANCELLED'
                        ? '已取消'
                        : past.status === 'TIMED_OUT'
                          ? '已超时'
                          : past.status
              }}
            </summary>
            <p>{{ readableJobError(past.error).summary || '本次执行没有记录错误原因。' }}</p>
            <p class="help-text">当次来源配置：{{ past.data_scope }}</p>
            <ul>
              <li
                v-for="stage in past.stages"
                :key="stage.stage_name"
              >
                {{ stageNames[stage.stage_name] || stage.stage_name }} · {{ stage.status }}
                <p v-if="stage.error">{{ readableJobError(stage.error).summary }}</p>
              </li>
            </ul>
          </details>
          <p
            v-if="!attempts.total"
            class="help-text"
          >
            尚无历史执行；本次重试前才会归档原状态。
          </p>
          <div class="button-row">
            <el-button
              :disabled="busy || attempts.page <= 1"
              @click="loadAttempts(attempts.page - 1)"
            >
              上一页
            </el-button>
            <el-button
              :disabled="busy || attempts.page * attempts.size >= attempts.total"
              @click="loadAttempts(attempts.page + 1)"
            >
              下一页
            </el-button>
          </div>
        </section>
        <p class="help-text">
          执行时间：{{ timestamp(selected.started_at) }} → {{ timestamp(selected.ended_at) }}
        </p>
        <el-alert
          v-if="selected.error"
          :title="readableJobError(selected.error).summary"
          type="error"
          :closable="false"
        />
        <FailureExplanation
          v-if="!selected.stages.some((stage) => stage.details?.diagnostic)"
          :diagnostic="selected.diagnostic"
          :context="`任务#${selected.id} / 当次错误记录释义`"
        />
        <details
          v-if="readableJobError(selected.error).technical"
          class="help-text"
        >
          <summary>原始技术信息（维护人员）</summary>
          <p>{{ readableJobError(selected.error).technical }}</p>
        </details>
        <ol class="stage-list">
          <li
            v-for="stage in selected.stages"
            :key="stage.stage_name"
          >
            <div>
              <strong>{{ stageNames[stage.stage_name] || stage.stage_name }}</strong>
              <StateBadge :value="stage.status" />
              <StateBadge
                :value="stage.data_status"
                :label="qualityLabel(stage.data_status)"
                context="quality"
                :explanation="qualityStatusExplanation({ job: selected, stage })"
              />
            </div>
            <small>{{ timestamp(stage.started_at) }} → {{ timestamp(stage.ended_at) }}</small>
            <p
              v-if="stage.details?.progress"
              class="help-text"
            >
              {{ stage.details.progress.message }} · {{ stage.details.progress.completed }} /
              {{ stage.details.progress.total || '待确定' }} · 最近进展
              {{ timestamp(stage.details.progress.updatedAt) }}
            </p>
            <p
              v-if="stage.details?.deadline"
              class="help-text"
            >
              阶段截止：{{ timestamp(stage.details.deadline) }}
            </p>
            <p
              v-if="stage.details?.recoveryMode === 'REVALIDATE_STORED_INPUTS'"
              class="help-text"
            >
              基于已保存数据重新核验，保留原始采集时刻。
            </p>
            <div
              v-if="stage.details?.quality"
              class="help-text"
            >
              <template v-if="stage.status === 'FAILED' && stage.details.quality.expected === 0">
                阶段未完成，尚未形成可核验项数；0 / 0不表示检查通过。
              </template>
              <template v-else>
                核验可用 {{ stage.details.quality.available }} /
                {{ stage.details.quality.expected }} 项。
              </template>
              <ul v-if="stage.details.quality.issues.length">
                <li
                  v-for="(issue, index) in stage.details.quality.issues"
                  :key="index"
                >
                  {{ issue.scope }}：{{ readableJobError(issue.reason).summary }}
                </li>
              </ul>
            </div>
            <FailureExplanation
              :diagnostic="stage.details?.diagnostic"
              :context="`任务#${selected.id} / ${stageNames[stage.stage_name] || stage.stage_name}`"
            />
            <details
              v-if="stage.error"
              class="stage-error"
            >
              <summary>阶段技术信息（维护人员）</summary>
              <p>
                {{
                  stage.error.startsWith('阶段失败，错误类型：') && selected.error
                    ? selected.error
                    : stage.error
                }}
              </p>
            </details>
          </li>
        </ol>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.job-latest-result {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.job-type-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}
.attempt-history {
  background: var(--surface-soft, #f6f8fb);
  border-radius: 12px;
  padding: 16px;
  margin: 16px 0;
}
.attempt-history__item {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color, #e5eaf0);
  overflow-wrap: anywhere;
}
.attempt-history__item summary {
  cursor: pointer;
  font-weight: 600;
}
</style>
