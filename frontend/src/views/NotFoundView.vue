<script setup lang="ts">
import SectionCard from '../components/common/SectionCard.vue';
import type { PageProps } from '../types/models';
defineProps<Partial<PageProps>>();
</script>
<template>
  <SectionCard>
    <el-empty description="找不到这个页面">
      <RouterLink
        to="/dashboard"
        class="text-link"
      >
        返回市场总览 →
      </RouterLink>
    </el-empty>
  </SectionCard>
</template>
