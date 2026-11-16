<script setup lang="ts">
import { computed } from 'vue';
import TermHelp from '../common/TermHelp.vue';
import type { ThemeStrength } from '../../types/models';
import { number, percent } from '../../utils/format';
const props = defineProps<{ strength: ThemeStrength; compact?: boolean }>();
const latest = computed(() => props.strength.items.at(-1));
</script>
<template>
  <dl
    class="theme-observation"
    :class="{ 'theme-observation--compact': compact }"
  >
    <div>
      <dt>样本评分 / 100</dt>
      <dd>{{ number(latest?.score, 1) }}</dd>
    </div>
    <div>
      <dt>
        <TermHelp
          v-if="!compact"
          term="rs20"
          label="20日相对强度"
        />
        <template v-else>20日相对强度</template>
      </dt>
      <dd>{{ percent(latest?.rs20, 2) }}</dd>
    </div>
    <div>
      <dt>
        <TermHelp
          v-if="!compact"
          term="breadth"
          label="站上MA20比例"
        />
        <template v-else>站上MA20比例</template>
      </dt>
      <dd>{{ percent(latest?.breadth) }}</dd>
    </div>
    <div>
      <dt>
        <TermHelp
          v-if="!compact"
          term="coverage"
          label="样本数据覆盖"
        />
        <template v-else>样本数据覆盖</template>
      </dt>
      <dd>{{ strength.members.length ? percent(latest?.coverage) : '—' }}</dd>
    </div>
  </dl>
  <p
    v-if="!compact"
    class="help-text"
  >
    最近观测：{{ latest?.trade_date || '尚无记录' }} · 当日样本等权收益
    {{ percent(latest?.day_return, 2) }}。
    相对强度以沪深300为基准，站上MA20表示收盘价高于20日均线。分值反映固定样本的历史相对表现，方法效果尚未完成历史验证。
  </p>
</template>
