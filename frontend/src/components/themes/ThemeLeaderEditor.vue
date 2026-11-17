<script setup lang="ts">
import { ref, watch } from 'vue';
import { useAuth } from '../../composables/useAuth';
import { useResource } from '../../composables/useResource';
import { useMutation } from '../../composables/useMutation';
import { getThemeLeaderRevision, updateThemeLeaders } from '../../api/theme-leaders';
import type { ThemeLeaders } from '../../types/theme-leaders';
import { today } from '../../utils/date';
const props = defineProps<{ code: string }>();
const emit = defineEmits<{ saved: [] }>();
const { user } = useAuth();
const opened = ref(false);
const draft = ref<ThemeLeaders>();
const revision = ref(0);
const notice = ref('');
const resource = useResource(
  async (signal) =>
    user.value?.role === 'ADMIN' && opened.value
      ? getThemeLeaderRevision(props.code, signal)
      : undefined,
  { watch: [() => props.code, opened], identity: () => props.code },
);
watch(resource.data, (value) => {
  if (value) {
    draft.value = JSON.parse(JSON.stringify(value.directory)) as ThemeLeaders;
    revision.value = value.revision;
    notice.value = value.needsReview ? '目录已超过90天未复核，请核对来源后更新。' : '';
  }
});
watch(
  () => props.code,
  () => {
    draft.value = undefined;
    notice.value = '';
  },
);
const mutation = useMutation();
async function save() {
  if (!draft.value) return;
  await mutation.run(async () => {
    const directory = JSON.parse(JSON.stringify(draft.value)) as ThemeLeaders;
    directory.reviewedAt = today();
    directory.members.forEach((m, i) => (m.displayOrder = i + 1));
    const savedCode = props.code;
    const value = await updateThemeLeaders(savedCode, {
      expectedRevision: revision.value,
      directory,
    });
    if (props.code !== savedCode) return;
    revision.value = value.revision;
    draft.value = value.directory;
    notice.value = '已追加当前目录修订，历史成分、评分与报告未改写。';
    emit('saved');
  });
}
function add() {
  draft.value?.members.push({
    displayOrder: draft.value.members.length + 1,
    symbol: '',
    name: '',
    tier: '细分代表',
    segment: '',
    standing: '',
    rationale: '',
    formalRanking: null,
    sources: [{ title: '', url: '' }],
  });
}
function move(index: number, delta: number) {
  if (!draft.value) return;
  const items = draft.value.members;
  const target = index + delta;
  if (target < 0 || target >= items.length) return;
  [items[index], items[target]] = [items[target]!, items[index]!];
}
</script>
<template>
  <details
    v-if="user?.role === 'ADMIN'"
    class="reading-details"
    @toggle="opened = ($event.target as HTMLDetailsElement).open"
  >
    <summary>
      <strong>维护当前核心企业目录</strong>
      <span>管理员 · 追加修订，不倒填历史</span>
    </summary>
    <p
      v-if="resource.error.value"
      role="alert"
    >
      {{ resource.error.value }}
    </p>
    <p
      v-if="notice"
      class="help-text"
    >
      {{ notice }}
    </p>
    <form
      v-if="draft"
      class="leader-editor"
      @submit.prevent="save"
    >
      <label>
        组名称
        <el-input
          v-model="draft.title"
          required
        />
      </label>
      <label>
        常用称呼
        <el-input v-model="draft.marketAlias" />
      </label>
      <label>
        入选和排序依据
        <el-input
          v-model="draft.selectionBasis"
          type="textarea"
          required
        />
      </label>
      <label>
        比较范围与方法边界
        <el-input
          v-model="draft.limitation"
          type="textarea"
          required
        />
      </label>
      <fieldset
        v-for="(member, index) in draft.members"
        :key="index"
      >
        <legend>{{ index + 1 }} · {{ member.name || '新增企业' }}</legend>
        <div class="member-fields">
          <label>
            证券代码
            <el-input
              v-model="member.symbol"
              placeholder="000000.SZ"
              required
            />
          </label>
          <label>
            公司简称
            <el-input
              v-model="member.name"
              required
            />
          </label>
          <label>
            梯队
            <el-input
              v-model="member.tier"
              required
            />
          </label>
          <label>
            业务环节
            <el-input
              v-model="member.segment"
              required
            />
          </label>
          <label>
            行业位置
            <el-input
              v-model="member.standing"
              required
            />
          </label>
          <label>
            入选事实
            <el-input
              v-model="member.rationale"
              type="textarea"
              required
            />
          </label>
          <label>
            同口径排名（选填，需年份与范围）
            <el-input v-model="member.formalRanking" />
          </label>
        </div>
        <div
          v-for="(source, i) in member.sources"
          :key="i"
          class="member-fields"
        >
          <label>
            来源标题
            <el-input
              v-model="source.title"
              required
            />
          </label>
          <label>
            原始HTTPS链接
            <el-input
              v-model="source.url"
              required
            />
          </label>
        </div>
        <div class="editor-actions">
          <el-button @click="move(index, -1)">上移</el-button>
          <el-button @click="move(index, 1)">下移</el-button>
          <el-button @click="draft.members.splice(index, 1)">移出当前目录</el-button>
        </div>
      </fieldset>
      <div class="editor-actions">
        <el-button @click="add">新增企业</el-button>
        <el-button
          native-type="submit"
          type="primary"
          :loading="mutation.busy.value"
        >
          按今日复核并保存修订
        </el-button>
      </div>
      <p
        v-if="mutation.error.value"
        role="alert"
      >
        {{ mutation.error.value }}
      </p>
      <p class="help-text">
        只登记核实过的公开事实。复核日期取上海当前日；企业展示次序不等于统一行业排名。当前修订 #{{
          revision
        }}。
      </p>
    </form>
  </details>
</template>
<style scoped>
.leader-editor {
  display: grid;
  gap: 14px;
}
.leader-editor label {
  display: grid;
  gap: 6px;
  font-size: 12px;
}
.leader-editor fieldset {
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  min-width: 0;
  padding: 16px;
}
.member-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.editor-actions .el-button {
  margin-left: 0;
}
@media (max-width: 700px) {
  .member-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
