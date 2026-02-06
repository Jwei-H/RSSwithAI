import path from 'node:path'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // 允许所有 host，解决 "Blocked request" 问题
    allowedHosts: true,
  },
  build: {
    target: 'esnext',
    sourcemap: false,
    reportCompressedSize: false,
  },
})