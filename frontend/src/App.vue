<script setup lang="ts">
import { ref, onMounted } from 'vue';
import LoginView from './views/LoginView.vue';
import { useAuth } from './composables/useAuth';
const { user, status, ready, error, initialize } = useAuth();
onMounted(initialize);
import AppShell from './layouts/AppShell.vue';
import { useResearchDate } from './composables/useResearchDate';
// 应用入口只维护共享业务日期与刷新信号，不加载页面业务数据。
const researchDate = useResearchDate({
  session: () =>
    ready.value && (user.value || status.value?.mode === 'LOCAL')
      ? String(user.value?.id ?? 'local')
      : '',
});
const { date } = researchDate;
const refreshToken = ref(0);
</script>
<template>
  <main
    v-if="!ready"
    class="login-screen"
    aria-live="polite"
  >
    正在确认访问状态…
  </main>
  <main
    v-else-if="error"
    class="login-screen"
  >
    <el-alert
      :title="error"
      type="error"
      :closable="false"
    />
    <el-button @click="initialize">重新连接</el-button>
  </main>
  <LoginView v-else-if="status?.mode === 'PUBLIC' && !user" />
  <AppShell
    v-else
    :key="user?.id ?? 'local'"
    :date="date"
    :date-context="researchDate.label.value"
    :date-warning="researchDate.warning.value"
    :date-loading="researchDate.loading.value"
    :date-ready="researchDate.ready.value"
    @update:date="researchDate.select"
    @latest-date="researchDate.latest"
    :refresh-token="refreshToken"
    @refresh="refreshToken++"
  >
    <RouterView v-slot="{ Component, route }">
      <component
        v-if="researchDate.ready.value || route.meta.hideBusinessDate === true"
        :is="Component"
        :key="route.path"
        :date="date"
        :refresh-token="refreshToken"
      />
      <p
        v-else
        class="notice notice--subtle"
        role="status"
      >
        正在确认已完成的收盘交易日；也可在页头手动选择研究日期。
      </p>
    </RouterView>
  </AppShell>
</template>
