<script setup lang="ts">
import { ref, computed } from 'vue';
import { savePlan } from '../../api/plans';
import { useMutation } from '../../composables/useMutation';
const props = defineProps<{ symbol: string; date: string; horizon: string; actionCode: string }>();
const mutation = useMutation();
const saved = ref('');
const submitted = ref('');
const identity = computed(
  () => `${props.symbol}:${props.date}:${props.horizon}:${props.actionCode}`,
);
async function save() {
  saved.value = '';
  const key = identity.value;
  submitted.value = key;
  const input = { ...props };
  await mutation.run(async () => {
    await savePlan(input);
    if (identity.value === key) saved.value = key;
  });
}
</script>
<template>
  <div class="plan-save">
    <el-button
      :loading="mutation.busy.value"
      @click="save"
    >
      保存为个人研究计划
    </el-button>
    <p
      v-if="mutation.error.value && submitted === identity"
      role="alert"
      class="help-text"
    >
      {{ mutation.error.value }}
    </p>
    <p
      v-if="saved === identity"
      class="help-text"
    >
      已冻结原始依据和价位。
      <RouterLink to="/plans">到研究计划跟踪 →</RouterLink>
    </p>
  </div>
</template>
<style scoped>
.plan-save {
  margin-top: 16px;
}
</style>
