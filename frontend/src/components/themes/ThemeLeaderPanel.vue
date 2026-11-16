<script setup lang="ts">
import SectionCard from '../common/SectionCard.vue';
import AsyncState from '../common/AsyncState.vue';
import { getThemeLeaders } from '../../api/theme-leaders';
import { useResource } from '../../composables/useResource';

const props = defineProps<{ code: string; researchDate: string; refreshToken: number }>();
const leaders = useResource((signal) => getThemeLeaders(props.code, signal), {
  watch: [() => props.code, () => props.refreshToken],
  identity: () => props.code,
});
const safeUrl = (value: string) => (/^https:\/\//i.test(value) ? value : '');
</script>

<template>
  <SectionCard
    :title="leaders.data.value?.title || '产业核心龙头'"
    description="先看行业位置，再看各自业务；核心龙头优先展示，不按当日涨跌或数据库顺序选股。"
  >
    <AsyncState
      :loading="leaders.loading.value"
      :error="leaders.error.value"
      :has-content="Boolean(leaders.data.value)"
      @retry="leaders.reload"
    >
      <template v-if="leaders.data.value">
        <div
          class="query-note"
          data-testid="leader-directory-context"
        >
          <strong>{{ leaders.data.value.marketAlias }}</strong>
          <p>{{ leaders.data.value.selectionBasis }}</p>
          <span>当前目录核对日期：{{ leaders.data.value.reviewedAt }}。</span>
          <span v-if="researchDate < leaders.data.value.reviewedAt">
            所选研究日期为
            {{ researchDate }}；这里展示后来整理的当前目录，不能认作当时已知的龙头名单。
          </span>
        </div>
        <div
          class="leader-grid"
          data-testid="industry-leaders"
        >
          <article
            v-for="member in leaders.data.value.members"
            :key="member.symbol"
            class="leader-card"
            :data-symbol="member.symbol"
          >
            <span class="leader-card__tier">{{ member.tier }}</span>
            <h3>
              {{ member.name }}
              <span>{{ member.symbol }}</span>
            </h3>
            <strong>{{ member.standing }}</strong>
            <p class="leader-card__segment">{{ member.segment }}</p>
            <p>{{ member.rationale }}</p>
            <p
              v-if="member.formalRanking"
              class="query-note"
            >
              来源中的明确名次：{{ member.formalRanking }}
            </p>
            <details class="leader-card__sources">
              <summary>入选依据与原始来源（{{ member.sources.length }}项）</summary>
              <ul>
                <li
                  v-for="source in member.sources"
                  :key="source.url"
                >
                  <a
                    v-if="safeUrl(source.url)"
                    :href="safeUrl(source.url)"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ source.title }}
                  </a>
                  <span v-else>{{ source.title }}（链接不可用）</span>
                </li>
              </ul>
            </details>
          </article>
        </div>
        <p
          class="help-text"
          data-testid="leader-ranking-boundary"
        >
          {{ leaders.data.value.limitation }}
        </p>
        <RouterLink
          to="/watchlist"
          class="text-link"
        >
          到自选股搜索并研究这些公司 →
        </RouterLink>
      </template>
    </AsyncState>
  </SectionCard>
</template>

<style scoped>
.leader-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  gap: 16px;
  margin: 20px 0;
}
.leader-card {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--el-border-color-light, #e2e8f0);
  border-radius: 12px;
}
.leader-card h3 {
  margin: 12px 0;
  font-size: 17px;
}
.leader-card h3 span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.leader-card p {
  line-height: 1.8;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.leader-card__tier {
  display: inline-block;
  padding: 3px 8px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
  font-size: 12px;
}
.leader-card__segment {
  color: var(--el-text-color-secondary);
}
.leader-card__sources {
  font-size: 12px;
  line-height: 1.8;
}
.leader-card__sources summary {
  cursor: pointer;
  color: var(--el-color-primary);
}
.leader-card__sources li {
  margin-top: 8px;
  overflow-wrap: anywhere;
}
</style>
