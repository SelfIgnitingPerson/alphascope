<script setup lang="ts">
import type { ComparablePeer } from '../../api/investment-evidence';
const props = defineProps<{ modelValue?: Partial<ComparablePeer> }>();
const emit = defineEmits<{ 'update:modelValue': [Partial<ComparablePeer>]; remove: [] }>();
function patch(value: Partial<ComparablePeer>) {
  emit('update:modelValue', { ...props.modelValue, ...value });
}
</script>
<template>
  <article class="valuation-peer">
    <div class="peer-grid">
      <label>
        同行代码
        <el-input
          :model-value="modelValue?.symbol"
          placeholder="完整代码，如600000.SH"
          @update:model-value="patch({ symbol: $event })"
        />
      </label>
      <label>
        同行每股面值（元）
        <el-input-number
          :model-value="modelValue?.parValue"
          :min="0"
          :controls="false"
          :precision="4"
          @update:model-value="patch({ parValue: $event })"
        />
      </label>
      <label>
        公开同行及面值材料
        <el-input
          :model-value="modelValue?.sourceUrl"
          placeholder="https://..."
          @update:model-value="patch({ sourceUrl: $event })"
        />
      </label>
      <label>
        可比性说明（至少20字）
        <el-input
          :model-value="modelValue?.rationale"
          type="textarea"
          :maxlength="1000"
          @update:model-value="patch({ rationale: $event })"
        />
      </label>
    </div>
    <el-button @click="emit('remove')">移除此同行</el-button>
  </article>
</template>
<style scoped>
.valuation-peer {
  padding: 14px 0;
  border-bottom: 1px solid #e2e9ef;
  margin-bottom: 12px;
}
.peer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 10px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}
.peer-grid :deep(.el-input-number) {
  width: 100%;
}
@media (max-width: 700px) {
  .peer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
