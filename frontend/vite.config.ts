import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': process.env.ALPHASCOPE_API_PROXY || 'http://127.0.0.1:8080' } },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/node_modules/zrender/')) return 'renderer';
          if (id.includes('/node_modules/echarts/')) return 'charts';
          if (
            id.includes('/node_modules/@vue/') ||
            id.includes('/node_modules/vue/') ||
            id.includes('/node_modules/vue-router/')
          )
            return 'framework';
        },
      },
    },
  },
});
