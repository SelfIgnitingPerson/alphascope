<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue';
import { RouterLink } from 'vue-router';
// 说明传送到body并固定定位，避免被表格overflow裁切；仅展开时监听外部事件。
const props = defineProps<{
  label: string;
  title: string;
  summary: string;
  detail?: string;
  example?: string;
  helpTo?: string;
  helpLabel?: string;
}>();
const open = ref(false);
const trigger = ref<HTMLButtonElement | null>(null);
const panel = ref<HTMLDivElement | null>(null);
const panelId = `research-help-${useId()}`;
const position = ref({ left: '12px', top: '12px', width: '360px', maxHeight: '600px' });
function cleanup() {
  document.removeEventListener('pointerdown', outside, true);
  document.removeEventListener('focusin', focusOutside, true);
  document.removeEventListener('keydown', keydown, true);
  window.removeEventListener('scroll', scroll, true);
  window.removeEventListener('resize', viewport);
}
function close(restoreFocus = false) {
  open.value = false;
  cleanup();
  if (restoreFocus) trigger.value?.focus({ preventScroll: true });
}
function inside(target: EventTarget | null) {
  return (
    target instanceof Node && (panel.value?.contains(target) || trigger.value?.contains(target))
  );
}
function outside(event: PointerEvent) {
  if (!inside(event.target)) close();
}
function focusOutside(event: FocusEvent) {
  if (!inside(event.target)) close();
}
function scroll(event: Event) {
  if (event.target instanceof Node && panel.value?.contains(event.target)) return;
  const bounds = trigger.value?.getBoundingClientRect();
  if (bounds && (bounds.bottom < 0 || bounds.top > window.innerHeight)) close();
  else positionPanel();
}
function viewport() {
  positionPanel();
}

function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopImmediatePropagation();
    close(true);
  }
}
// 滚动时跟随锚点，避免浏览器尚未派发完的自动滚动事件把刚打开的说明关闭。
function positionPanel() {
  if (!open.value || !trigger.value || !panel.value) return;
  const bounds = trigger.value.getBoundingClientRect();
  const width = Math.min(360, Math.max(0, window.innerWidth - 24));
  const maxHeight = Math.max(40, window.innerHeight - 24);
  const height = Math.min(panel.value.scrollHeight, maxHeight);
  const top =
    bounds.bottom + 8 + height <= window.innerHeight - 12
      ? bounds.bottom + 8
      : Math.max(12, bounds.top - height - 8);
  position.value = {
    left: `${Math.max(12, Math.min(bounds.left, window.innerWidth - width - 12))}px`,
    top: `${Math.min(top, Math.max(12, window.innerHeight - height - 12))}px`,
    width: `${width}px`,
    maxHeight: `${maxHeight}px`,
  };
}
async function toggle() {
  if (open.value) {
    close();
    return;
  }
  open.value = true;
  await nextTick();
  if (!open.value || !trigger.value || !panel.value) return;
  positionPanel();
  document.addEventListener('pointerdown', outside, true);
  document.addEventListener('focusin', focusOutside, true);
  document.addEventListener('keydown', keydown, true);
  window.addEventListener('scroll', scroll, true);
  window.addEventListener('resize', viewport);
  panel.value.focus({ preventScroll: true });
}
watch([() => props.title, () => props.summary, () => props.detail], async () => {
  if (!open.value) return;
  await nextTick();
  positionPanel();
});
onBeforeUnmount(() => close());
</script>
<template>
  <span class="research-help-anchor">
    <button
      ref="trigger"
      type="button"
      class="research-help-trigger"
      :aria-label="`${props.label}：查看说明`"
      aria-haspopup="dialog"
      :aria-expanded="open"
      :aria-controls="open ? panelId : undefined"
      :title="`${props.label}：${summary} 点击查看说明`"
      @click.stop.prevent="toggle"
    >
      <slot>{{ label }}</slot>
      <span
        class="research-help-mark"
        aria-hidden="true"
      >
        ?
      </span>
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panel"
        role="dialog"
        :aria-label="title"
        tabindex="-1"
        class="research-help-panel"
        :style="position"
        data-testid="research-help-popover"
      >
        <header>
          <strong>{{ title }}</strong>
          <button
            type="button"
            aria-label="关闭说明"
            @click="close(true)"
          >
            ×
          </button>
        </header>
        <p>{{ summary }}</p>
        <slot name="details" />
        <p
          v-if="example"
          class="research-help-example"
        >
          例：{{ example }}
        </p>
        <p
          v-if="detail"
          class="research-help-detail"
        >
          {{ detail }}
        </p>
        <RouterLink
          v-if="helpTo"
          :to="helpTo"
          class="text-link"
          @click="close()"
        >
          {{ helpLabel || '查看完整释义与相关术语' }} →
        </RouterLink>
      </div>
    </Teleport>
  </span>
</template>
<style scoped>
.research-help-anchor {
  display: inline;
}
.research-help-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  color: inherit;
  font: inherit;
  line-height: inherit;
  text-align: inherit;
  background: none;
  border: 0;
  padding: 0;
  cursor: help;
  vertical-align: baseline;
}
.research-help-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  min-width: 14px;
  border: 1px solid #94a3b8;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 600;
  color: #64748b;
  line-height: 1;
}
.research-help-trigger:focus-visible {
  outline: 2px solid #5b7eaa;
  outline-offset: 3px;
  border-radius: 4px;
}
.research-help-panel {
  position: fixed;
  z-index: 4000;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  background: #fff;
  color: #27364a;
  box-shadow: 0 12px 36px #0f172a24;
  font-size: 13px;
  line-height: 1.8;
  text-align: left;
}
.research-help-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.research-help-panel header button {
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  padding: 2px 5px;
}
.research-help-panel p {
  margin: 10px 0;
  overflow-wrap: anywhere;
}
.research-help-detail {
  color: #536579;
}
.research-help-example {
  background: #f1f5f9;
  padding: 8px 10px;
  border-radius: 6px;
}
</style>
