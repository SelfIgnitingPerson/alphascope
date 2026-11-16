<script setup lang="ts">
import { nextTick, ref, useId } from 'vue';

/** 工作区内容分类；切换时保留面板实例，避免重置表单、选中结果和后台任务轮询。 */
const props = defineProps<{
  modelValue: string;
  label: string;
  items: readonly { id: string; label: string; description?: string }[];
}>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const tabId = `section-tabs-${useId()}`;
const navigation = ref<HTMLElement>();
async function move(event: KeyboardEvent) {
  const index = props.items.findIndex((item) => item.id === props.modelValue);
  let target: number;
  switch (event.key) {
    case 'ArrowRight':
      target = (index + 1) % props.items.length;
      break;
    case 'ArrowLeft':
      target = (index + props.items.length - 1) % props.items.length;
      break;
    case 'Home':
      target = 0;
      break;
    case 'End':
      target = props.items.length - 1;
      break;
    default:
      return;
  }
  event.preventDefault();
  const item = props.items[target];
  if (!item) return;
  emit('update:modelValue', item.id);
  await nextTick();
  navigation.value?.querySelector<HTMLButtonElement>('[aria-selected="true"]')?.focus();
}
</script>
<template>
  <div class="section-tabs">
    <nav
      ref="navigation"
      class="section-tabs__navigation"
      role="tablist"
      :aria-label="label"
      @keydown="move"
    >
      <button
        v-for="item in items"
        :id="`${tabId}-${item.id}`"
        :key="item.id"
        type="button"
        role="tab"
        :aria-selected="modelValue === item.id"
        :aria-controls="`${tabId}-${item.id}-panel`"
        :tabindex="modelValue === item.id ? 0 : -1"
        @click="emit('update:modelValue', item.id)"
      >
        {{ item.label }}
      </button>
    </nav>
    <section
      v-for="item in items"
      v-show="modelValue === item.id"
      :id="`${tabId}-${item.id}-panel`"
      :key="item.id"
      role="tabpanel"
      :aria-labelledby="`${tabId}-${item.id}`"
      class="section-tabs__panel"
      tabindex="0"
    >
      <p
        v-if="item.description"
        class="section-tabs__description"
      >
        {{ item.description }}
      </p>
      <slot :name="item.id" />
    </section>
  </div>
</template>
