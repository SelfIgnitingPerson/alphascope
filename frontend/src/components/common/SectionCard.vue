<script setup lang="ts">
defineProps<{ title?: string; description?: string }>();
</script>
<template>
  <section class="section-card">
    <header
      v-if="title || $slots.actions"
      class="section-card__header"
    >
      <div>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="section-card__actions"><slot name="actions" /></div>
    </header>
    <div class="section-card__body"><slot /></div>
  </section>
</template>
