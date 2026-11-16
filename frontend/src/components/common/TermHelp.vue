<script setup lang="ts">
import { computed } from 'vue';
import { findTerm } from '../../content/research-terms';
import HelpPopover from './HelpPopover.vue';
const props = defineProps<{ term?: string; label: string }>();
const definition = computed(() => findTerm(props.term));
</script>
<template>
  <HelpPopover
    v-if="definition"
    :label="label"
    :title="definition.name"
    :summary="definition.meaning"
    :example="definition.example"
    :detail="definition.note"
    :help-to="`/terms?term=${encodeURIComponent(definition.id)}`"
  >
    <slot>{{ label }}</slot>
  </HelpPopover>
  <span v-else>
    <slot>{{ label }}</slot>
  </span>
</template>
