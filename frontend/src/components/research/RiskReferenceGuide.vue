<script setup lang="ts">
import TermHelp from '../common/TermHelp.vue';
</script>
<template>
  <p class="risk-reference-guide">
    <TermHelp
      term="original-risk-reference"
      label="风险参考怎么看"
    />
    <span>不是买入价。未触及不代表可以买入；触及或跌破后需重新评估原方案。</span>
  </p>
</template>
<style scoped>
.risk-reference-guide {
  color: #60758b;
  font-size: 12px;
  line-height: 1.9;
  margin: 10px 0 16px;
}
.risk-reference-guide span {
  margin-left: 8px;
}
</style>
