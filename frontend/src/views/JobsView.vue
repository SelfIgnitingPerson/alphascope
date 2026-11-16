<script setup lang="ts">
import { readableJobError } from '../utils/job-errors';
import { qualityStatusExplanation } from '../content/status-explanations';
import FailureExplanation from '../components/common/FailureExplanation.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import StateBadge from '../components/common/StateBadge.vue';
import { useResource } from '../composables/useResource';
import { useMutation } from '../composables/useMutation';
import type { PageProps, JobDetail, ReportType } from '../types/models';
import { ref, computed, watch, onScopeDispose } from 'vue';
import { getJobs, getJob, runJob, retryJob, cancelJob } from '../api/jobs';
import { getProviders } from '../api/market';
import { stageNames, timestamp } from '../utils/format';
const props = defineProps<PageProps>();
const page = ref(1),
  size = ref(20),
  force = ref(false);
const { data, loading, error, reload } = useResource(
  (signal) => getJobs({ page: page.value, size: size.value }, signal),
  [page, () => props.refreshToken],
);
const { data: providerStatus } = useResource(getProviders, [() => props.refreshToken]);
const currentScope = computed(() => providerStatus.value?.dataScope);
const isHistoricalScope = (scope: string) =>
  Boolean(currentScope.value && scope !== currentScope.value);
const { busy, error: mutationError, run } = useMutation();
const selected = ref<JobDetail>(),
  drawer = ref(false),
  notice = ref('');
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
          result.data_status === 'PARTIAL' || result.data_status === 'UNAVAILABLE'
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
      description="演示、真实数据与不同通道配置分别记录，历史失败保留以便追踪。"
    >
      <template #actions><el-button @click="refresh">刷新记录</el-button></template>
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
              {{ s.row.job_name === 'DAILY' ? '收盘日报' : '每周周报' }}
            </template>
          </el-table-column>
          <el-table-column
            label="数据范围"
            min-width="170"
          >
            <template #default="s">
              <span class="scope-label">
                {{
                  s.row.data_scope?.startsWith('REAL:')
                    ? '真实 · ' + s.row.data_scope.slice(5)
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
            prop="attempt"
            label="次数"
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
            {{ selected.business_date }} · {{ selected.job_name === 'DAILY' ? '日报' : '周报' }}
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
          任务 #{{ selected.id }} · 第 {{ selected.attempt }} 次尝试 · 最近心跳
          {{ timestamp(selected.heartbeat_at) }}
          <span v-if="selected.retry_of">· 重试自 #{{ selected.retry_of }}</span>
        </p>
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
