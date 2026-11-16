<script setup lang="ts">
import { computed } from 'vue';
import AsyncState from '../components/common/AsyncState.vue';
import StateBadge from '../components/common/StateBadge.vue';
import ThemeObservation from '../components/themes/ThemeObservation.vue';
import { useResource } from '../composables/useResource';
import { getThemeLeadersCatalog } from '../api/theme-leaders';
import type { PageProps } from '../types/models';
import { getThemes, getThemeStrength } from '../api/market';
import { daysBefore } from '../utils/date';
const props = defineProps<PageProps>();
const leaders = useResource(getThemeLeadersCatalog, [() => props.refreshToken]);
const leaderNames = (code: string) =>
  leaders.data.value
    ?.find((item) => item.themeCode === code)
    ?.members.map((item) => item.name)
    .join('、');
// 只读取已经保存的研究记录；单个主题读取失败不遮住其他主题。
const { data, loading, error, reload } = useResource(
  async (signal) => {
    const date = props.date;
    const themes = await getThemes(signal);
    return Promise.all(
      themes.map(async (theme) => {
        try {
          const strength = await getThemeStrength(
            theme.code,
            { start: daysBefore(date, 90), end: date },
            signal,
          );
          return { theme, strength, error: '' };
        } catch (reason) {
          if (signal.aborted) throw reason;
          return {
            theme,
            strength: undefined,
            error: reason instanceof Error ? reason.message : '研究记录读取失败',
          };
        }
      }),
    );
  },
  { watch: [() => props.date, () => props.refreshToken], identity: () => props.date },
);
const ready = computed(() => (data.value || []).filter((item) => item.strength?.members.length));
const pending = computed(() => (data.value || []).filter((item) => !item.strength?.members.length));
const groups = computed(() =>
  [
    {
      title: '已有样本研究',
      description: '展示固定研究样本的实际观测，点击可核对完整成员与公开来源。',
      items: ready.value,
    },
    {
      title: '待建设与待核对',
      description: '这些分类尚缺有效成员或读取结果，不能评定主题强弱。',
      items: pending.value,
    },
  ].filter((group) => group.items.length),
);
const descriptions: Record<string, string> = {
  CPO: '算力互联 · 光模块与共封装光学',
  PCB: '算力硬件 · PCB 与覆铜板',
  COOLING: '基础设施 · 液冷与热管理',
  SEMI_EQUIPMENT: '制造环节 · 半导体设备',
  MEMORY: '计算存储 · 存储芯片与配套',
};
</script>
<template>
  <div class="page-stack">
    <div
      v-if="data"
      class="notice notice--subtle"
    >
      <strong>AI产业链研究覆盖</strong>
      <span>
        截至 {{ date }}，已有样本研究 {{ ready.length }} 个主题，待建设或待核对
        {{ pending.length }} 个。这里只统计本机已有研究，样本覆盖率不代表完整行业覆盖率。
      </span>
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      @retry="reload"
    >
      <section
        v-for="group in groups"
        :key="group.title"
        class="theme-group"
      >
        <header class="theme-group__heading">
          <h2>{{ group.title }}</h2>
          <p>{{ group.description }}</p>
        </header>
        <div class="theme-grid">
          <RouterLink
            v-for="item in group.items"
            :key="item.theme.code"
            :to="`/themes/${item.theme.code}`"
            class="theme-card"
          >
            <span class="theme-card__code">{{ item.theme.code }}</span>
            <h2>{{ item.theme.name }}</h2>
            <p>
              {{
                item.theme.description || descriptions[item.theme.code] || '查看有效成分与研究证据'
              }}
            </p>
            <p
              v-if="leaderNames(item.theme.code)"
              class="theme-card__leaders"
            >
              当前核心与细分观察：{{ leaderNames(item.theme.code) }}
            </p>
            <div class="theme-card__status">
              <StateBadge
                :value="
                  item.error
                    ? 'UNAVAILABLE'
                    : item.strength?.items.at(-1)?.is_synthetic
                      ? 'SYNTHETIC'
                      : item.strength?.members.length
                        ? 'OBSERVED'
                        : 'NOT_CONFIGURED'
                "
                :label="
                  item.error
                    ? '读取失败'
                    : item.strength?.items.at(-1)?.is_synthetic
                      ? '演示记录'
                      : item.strength?.members.length
                        ? '已有样本'
                        : '尚未建立样本'
                "
                :explain="false"
              />
            </div>
            <template v-if="item.strength?.members.length">
              <div class="theme-card__scope">
                {{ item.strength.cohort?.title || `已登记成员 ${item.strength.members.length} 只` }}
              </div>
              <ThemeObservation
                :strength="item.strength"
                compact
              />
              <p class="theme-card__note">
                {{ item.strength.members.map((member) => member.name).join('、') }}
              </p>
              <p class="theme-card__note">
                最近观测 {{ item.strength.items.at(-1)?.trade_date || '尚无记录' }} ·
                完整行业覆盖未核实
              </p>
            </template>
            <p
              v-else
              class="theme-card__note"
            >
              {{
                item.error ||
                '当前有效成员为0。需先补充有公开依据的成员及生效日期，再采集同源行情和计算指标。反复刷新不会补齐这些资料。'
              }}
            </p>
            <span class="text-link">
              {{
                item.strength?.members.length ? '查看样本、历史与来源 →' : '查看缺项与建设条件 →'
              }}
            </span>
          </RouterLink>
        </div>
      </section>
      <el-empty
        v-if="data?.length === 0"
        description="尚未登记产业链分类"
      />
    </AsyncState>
  </div>
</template>
