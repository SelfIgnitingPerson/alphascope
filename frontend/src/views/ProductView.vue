<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import SectionTabs from '../components/common/SectionTabs.vue';
import SourceLicensePanel from '../components/product/SourceLicensePanel.vue';
import AsyncState from '../components/common/AsyncState.vue';
import SectionCard from '../components/common/SectionCard.vue';
import type { PageProps } from '../types/models';
import { useResource } from '../composables/useResource';
import { useMutation } from '../composables/useMutation';
import { useAuth } from '../composables/useAuth';
import {
  getProductStatus,
  declareLicense,
  getManagedUsers,
  updateManagedUser,
  type LicenseInput,
  type ManagedUser,
} from '../api/product';
import { today } from '../utils/date';
import { sourceName, timestamp } from '../utils/format';
const props = defineProps<PageProps>();
const { user } = useAuth();
const activeTab = ref('readiness');
const tabs = [
  { id: 'readiness', label: '运行就绪' },
  { id: 'licenses', label: '数据使用许可' },
  { id: 'accounts', label: '账号支持' },
];
const { data, loading, error, reload } = useResource(getProductStatus, [() => props.refreshToken]);
const licenseSources = computed(() => [
  ...new Set([...(data.value?.licenses.map((item) => item.source) || []), 'BIGQUANT']),
]);
const page = ref(1);
const users = useResource(() => getManagedUsers(page.value));
const mutation = useMutation();
const { busy, error: mutationError } = mutation;
const saved = ref('');
const license = reactive<LicenseInput>({
  source: '',
  permissions: [],
  evidenceUrl: '',
  reference: '',
  validFrom: today(),
  validUntil: '',
});
const resetUser = ref<ManagedUser>();
const newPassword = ref('');
const labels: Record<string, string> = {
  DISPLAY: '对外展示',
  COLLECT: '采集保存',
  EXPORT: '证据导出',
  DERIVED_REPORT: '生成研究报告',
};
async function saveLicense() {
  await mutation.run(async () => {
    await declareLicense({ ...license });
    saved.value = '已保存人工许可登记。登记本身不产生供应商授权，请保留实际合同或条款依据。';
    await reload();
  });
}
async function toggleAccount(row: ManagedUser) {
  await mutation.run(async () => {
    await updateManagedUser(row.id, row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
    await users.reload();
  });
}
async function resetPassword() {
  const selected = resetUser.value;
  if (!selected) return;
  await mutation.run(async () => {
    await updateManagedUser(selected.id, 'ACTIVE', newPassword.value);
    newPassword.value = '';
    resetUser.value = undefined;
    saved.value = '密码已更新，全部旧会话已撤销，请通过安全渠道告知账号本人。';
    await users.reload();
  });
}
async function changePage(value: number) {
  page.value = value;
  await users.reload();
}
</script>
<template>
  <div
    v-if="user?.role !== 'ADMIN'"
    class="notice notice--warning"
  >
    此页面仅管理员可访问。
  </div>
  <template v-else>
    <el-alert
      v-if="mutationError"
      :title="mutationError"
      type="error"
      :closable="false"
    />
    <el-alert
      v-if="saved"
      :title="saved"
      type="success"
      :closable="true"
      @close="saved = ''"
    />
    <SectionTabs
      v-model="activeTab"
      label="产品管理内容分类"
      :items="tabs"
    >
      <template #readiness>
        <AsyncState
          :loading="loading"
          :error="error"
          :has-content="Boolean(data)"
          @retry="reload"
        >
          <template v-if="data">
            <SectionCard
              title="产品运行与发布检查"
              description="健康UP不代表数据已更新，也不代表已获得商业使用许可。"
            >
              <p>
                核查时间：{{ timestamp(data.workspace.checkedAt) }} · 应有收盘日：{{
                  data.workspace.expectedTradeDate || '权威日历不足'
                }}
              </p>
              <p>
                当前账号：{{ data.workspace.capacity.activeJobs }} 个研究任务 /
                {{ data.workspace.capacity.activeValidations }} 个验证任务；验证证据
                {{ (data.workspace.capacity.evidenceBytes / 1048576).toFixed(2) }} MiB。
              </p>
              <h3>影响当前研究的问题</h3>
              <p
                v-if="!data.runtimeIssues?.length"
                class="help-text"
              >
                当前未发现运行阻断；数据范围与研究效果请分别核对。
              </p>
              <ul>
                <li
                  v-for="issue in data.runtimeIssues || []"
                  :key="issue"
                >
                  {{ issue }}
                </li>
              </ul>
              <details class="reading-details">
                <summary><strong>对外发布前待办（与本机故障分开）</strong></summary>
                <ul>
                  <li
                    v-for="item in data.blockers"
                    :key="item"
                  >
                    {{ item }}
                  </li>
                </ul>
                <p class="help-text">
                  本机研究可以继续；这些待办需要实际授权、运行和交付证据完成验收。
                </p>
                <el-button
                  link
                  @click="activeTab = 'licenses'"
                >
                  核对来源与已登记用途
                </el-button>
              </details>
              <p class="help-text">
                自动备份{{
                  data.backupEnabled ? '已启用' : '未启用'
                }}；持久卷不等于恢复点。来源、持续运行和负载需要在实际部署中验收。
              </p>
            </SectionCard>
          </template>
        </AsyncState>
      </template>
      <template #licenses>
        <AsyncState
          :loading="loading"
          :error="error"
          :has-content="Boolean(data)"
          @retry="reload"
        >
          <template v-if="data">
            <SourceLicensePanel
              :licenses="data.licenses"
              :enforced="data.sourceLicenseEnforced"
            >
              <form
                class="login-form"
                @submit.prevent="saveLicense"
              >
                <label>数据来源</label>
                <el-select
                  v-model="license.source"
                  placeholder="选择已配置来源"
                >
                  <el-option
                    v-for="source in licenseSources"
                    :key="source"
                    :value="source"
                    :label="sourceName(source)"
                  />
                </el-select>
                <label>实际取得的使用范围</label>
                <el-select
                  v-model="license.permissions"
                  multiple
                  placeholder="按授权范围选择"
                >
                  <el-option
                    v-for="(label, key) in labels"
                    :key="key"
                    :value="key"
                    :label="label"
                  />
                </el-select>
                <label>使用条款或授权依据链接（HTTPS）</label>
                <el-input
                  v-model="license.evidenceUrl"
                  placeholder="填写实际条款或合同依据的 HTTPS 链接"
                  maxlength="1000"
                  required
                />
                <label>合同或条款定位</label>
                <el-input
                  v-model="license.reference"
                  maxlength="200"
                  required
                />
                <label>许可开始日期</label>
                <el-date-picker
                  v-model="license.validFrom"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择许可开始日期"
                  :clearable="false"
                />
                <label>许可截止日期</label>
                <el-date-picker
                  v-model="license.validUntil"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择许可截止日期"
                  :clearable="false"
                />
                <el-button
                  native-type="submit"
                  type="primary"
                  :loading="busy"
                >
                  保存本次登记
                </el-button>
              </form>
            </SourceLicensePanel>
          </template>
        </AsyncState>
      </template>
      <template #accounts>
        <SectionCard
          title="账号支持"
          description="仅管理启用状态及密码恢复，不查看其他账号的私人研究内容。停用或重置会撤销旧会话。"
        >
          <AsyncState
            :loading="users.loading.value"
            :error="users.error.value"
            @retry="users.reload"
          >
            <el-table :data="users.data.value?.items || []">
              <el-table-column
                prop="username"
                label="账号"
              />
              <el-table-column
                prop="role"
                label="角色"
              />
              <el-table-column
                prop="status"
                label="状态"
              />
              <el-table-column
                label="操作"
                min-width="180"
              >
                <template #default="s">
                  <el-button
                    link
                    :disabled="busy || s.row.id === 1"
                    @click="toggleAccount(s.row)"
                  >
                    {{ s.row.status === 'ACTIVE' ? '停用' : '启用' }}
                  </el-button>
                  <el-button
                    link
                    :disabled="busy"
                    @click="
                      resetUser = s.row;
                      newPassword = '';
                    "
                  >
                    恢复密码
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              :current-page="page"
              :page-size="20"
              :total="users.data.value?.total || 0"
              layout="prev, pager, next"
              @current-change="changePage"
            />
          </AsyncState>
          <form
            v-if="resetUser"
            class="account-reset-form"
            @submit.prevent="resetPassword"
          >
            <label>为 {{ resetUser.username }} 设置新密码</label>
            <el-input
              v-model="newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="至少12字符"
              minlength="12"
              maxlength="128"
              required
            />
            <el-button
              native-type="submit"
              :loading="busy"
            >
              保存并撤销旧会话
            </el-button>
            <el-button
              @click="
                resetUser = undefined;
                newPassword = '';
              "
            >
              取消
            </el-button>
          </form>
        </SectionCard>
      </template>
    </SectionTabs>
  </template>
</template>
