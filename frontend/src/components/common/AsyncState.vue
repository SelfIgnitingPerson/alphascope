<script setup lang="ts">
defineProps<{ loading: boolean; error?: string; hasContent?: boolean }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div
    class="async-state"
    :aria-busy="loading"
  >
    <div
      v-if="error"
      class="error-state"
      role="alert"
    >
      <div>
        <strong>暂时无法加载</strong>
        <p>{{ error }}</p>
      </div>
      <el-button @click="$emit('retry')">重新加载</el-button>
    </div>
    <el-skeleton
      v-if="loading && !hasContent"
      :rows="5"
      animated
      class="loading-state"
    />
    <p
      v-if="error && hasContent"
      class="help-text"
    >
      以下保留上次成功读取的内容，请按数据日期复核。
    </p>
    <p
      v-if="loading && hasContent"
      class="help-text"
      role="status"
    >
      正在刷新，以下为本次查询上次成功读取的内容。
    </p>
    <slot v-if="hasContent || (!loading && !error)" />
  </div>
</template>
