/// <reference types="vitest/config" />
import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:3001';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // Proxy API calls to the backend in dev so the app uses same-origin '/api'.
    proxy: {
      '/api': { target: BACKEND_URL, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
});
