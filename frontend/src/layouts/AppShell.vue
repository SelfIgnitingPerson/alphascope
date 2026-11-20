<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { getOverview } from '../api/market';
import { useResource } from '../composables/useResource';
import { navigation } from '../router/navigation';
import { useAuth } from '../composables/useAuth';
const { user, status: access, signOut } = useAuth();
const visibleNavigation = computed(() =>
  navigation
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => !['/operations', '/product'].includes(item.path) || user.value?.role === 'ADMIN',
      ),
    }))
    .filter((group) => group.items.length),
);
import { sourceName } from '../utils/format';
import type { PageProps } from '../types/models';
import AppIcon from '../components/common/AppIcon.vue';
const props = defineProps<
  PageProps & {
    dateContext?: string;
    dateWarning?: string;
    dateLoading?: boolean;
    dateReady?: boolean;
  }
>();
defineEmits<{ 'update:date': [value: string]; refresh: []; 'latest-date': [] }>();
const route = useRoute();
const mobileOpen = ref(false);
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false;
  },
);
const { data: overview, error } = useResource(
  (signal) =>
    props.dateReady === false ? Promise.resolve(undefined) : getOverview(props.date, signal),
  {
    watch: [() => props.date, () => props.refreshToken, () => props.dateReady],
    identity: () => props.date,
  },
);
const group = computed(
  () =>
    navigation.find((group) => group.items.some((item) => item.path === route.meta.section))
      ?.label || '工作台',
);
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar__top">
        <RouterLink
          to="/dashboard"
          class="brand"
        >
          <span class="brand__mark">α</span>
          <span>
            <strong>AlphaScope</strong>
            <small>阿尔法市场洞察</small>
          </span>
        </RouterLink>
        <button
          class="mobile-menu-toggle"
          type="button"
          aria-controls="workspace-navigation"
          :aria-expanded="mobileOpen"
          @click="mobileOpen = !mobileOpen"
        >
          <AppIcon name="overview" />
          导航
        </button>
      </div>
      <nav
        id="workspace-navigation"
        class="navigation"
        :class="{ 'is-open': mobileOpen }"
        aria-label="主导航"
      >
        <section
          v-for="group in visibleNavigation"
          :key="group.label"
          class="navigation__group"
        >
          <h2>{{ group.label }}</h2>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :aria-label="item.label"
            class="navigation__link"
            :class="{ 'is-active': route.meta.section === item.path }"
          >
            <AppIcon :name="item.icon" />
            <span>{{ item.label }}</span>
            <span class="navigation__dot" />
          </RouterLink>
        </section>
      </nav>
      <div class="sidebar__note">
        <span class="live-dot" />
        {{ access?.mode === 'PUBLIC' ? '独立账号研究工作台' : '本地研究工作台' }}
        <p>
          研究指标仅供复核
          <br />
          不提供自动交易与收益保证
        </p>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          {{ group }}
          <span>/</span>
          <strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar__status">
          <RouterLink
            to="/terms"
            class="text-link"
          >
            术语与状态
          </RouterLink>
          <span
            v-if="user"
            class="topbar__account"
            :title="user.username"
          >
            {{ user.username }}
          </span>
          <el-button
            data-testid="auth-logout"
            v-if="access?.mode === 'PUBLIC'"
            link
            @click="signOut"
          >
            退出登录
          </el-button>
          <span class="topbar__mode">
            <span
              class="live-dot"
              :class="{ 'live-dot--warning': overview?.isSynthetic || error }"
            />
            {{
              error
                ? '数据服务待检查'
                : !overview
                  ? '状态加载中'
                  : overview.isSynthetic
                    ? '演示模式'
                    : '真实行情模式'
            }}
          </span>
          <span class="topbar__separator" />
          <span>{{ sourceName(overview?.provider) }}</span>
        </div>
      </header>
      <main class="page-container">
        <header class="page-heading">
          <div>
            <p class="eyebrow">ALPHASCOPE / RESEARCH WORKSPACE</p>
            <h1>{{ route.meta.title }}</h1>
            <p class="page-description">{{ route.meta.description }}</p>
          </div>
          <div
            v-if="route.meta.hideBusinessDate !== true"
            class="date-toolbar"
          >
            <label for="business-date">研究日期</label>
            <el-date-picker
              id="business-date"
              :model-value="date"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              @update:model-value="$emit('update:date', String($event))"
            />
            <el-button
              data-testid="latest-research-date"
              :loading="dateLoading"
              @click="$emit('latest-date')"
            >
              最近收盘日
            </el-button>
            <el-button @click="$emit('refresh')">刷新</el-button>
          </div>
          <el-button
            v-else-if="!['/help', '/terms'].includes(route.path)"
            @click="$emit('refresh')"
          >
            {{ route.path.startsWith('/reports/') ? '刷新报告' : '刷新状态' }}
          </el-button>
        </header>
        <p
          v-if="route.meta.hideBusinessDate !== true && dateContext"
          class="date-context"
          role="status"
        >
          {{ dateContext }} · {{ date }}；切换日期仅读取对应记录，不自动采集。
        </p>
        <div
          v-if="route.meta.hideBusinessDate !== true && dateWarning"
          class="notice notice--warning"
          role="status"
        >
          {{ dateWarning }}
        </div>
        <div
          v-if="overview?.isSynthetic"
          class="notice notice--warning"
          role="status"
        >
          <strong>当前为模拟数据</strong>
          <span>行情、日历和报告用于演示，不生成真实投资预警。</span>
        </div>
        <div
          v-if="error"
          class="notice notice--warning"
          role="status"
        >
          <strong>全局数据状态暂不可用</strong>
          <span>各页面仍可独立加载，请检查行情服务。</span>
        </div>
        <slot />
        <footer class="page-footer">
          <span>AlphaScope · 可追溯的市场研究</span>
          <span>价格：人民币元 · 数量：股 · 业务时区：上海</span>
        </footer>
      </main>
    </div>
  </div>
</template>

<style scoped>
.date-context {
  margin: -12px 0 24px;
  font-size: 12px;
  color: #738398;
  line-height: 1.8;
}
</style>
