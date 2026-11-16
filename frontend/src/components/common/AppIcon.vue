<script setup lang="ts">
defineProps<{ name: string }>();
const paths: Record<string, string> = {
  help: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M9.5 9a2.5 2.5 0 1 1 4.3 1.7c-1.1.6-1.8 1.1-1.8 2.8 M12 17h.01',
  overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  themes: 'M12 3v5 M4 16v5 M20 16v5 M4 16v-4h16v4 M12 8v13',
  watchlist: 'm12 3 2.8 5.8 6.4.9-4.6 4.5 1.1 6.4-5.7-3-5.7 3 1.1-6.4L2.8 9.7l6.4-.9Z',
  portfolio: 'M3 7h18v14H3z M8 7V3h8v4 M3 12h18 M10 12v4h4v-4',
  reports: 'M6 3h9l4 4v14H6z M14 3v5h5 M9 12h7 M9 16h7',
  history: 'M4 6v5h5 M4 10a8 8 0 1 1 1 7 M12 7v5l3 2',
  jobs: 'm4 4 16 8-16 8z',
  channels: 'M5 3v5 M19 3v5 M3 8h4v7H3z M17 8h4v7h-4z M5 15v3h14v-3 M12 18v3',
};
</script>
<template>
  <svg
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.overview" />
  </svg>
</template>
