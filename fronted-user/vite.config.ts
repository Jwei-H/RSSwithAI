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
  build: {
    target: 'esnext',
    minify: 'esbuild', // Open esbuild minification (fast, good effect)
    cssMinify: true,   // Open CSS minification
    sourcemap: false,
    reportCompressedSize: false,
    chunkSizeWarningLimit: 2048,
    rollupOptions: {
      output: {
        manualChunks: {
          mermaid: ['mermaid'],
          echarts: ['echarts', 'echarts-wordcloud'],
        },
      },
    },
  },
  esbuild: {
    legalComments: 'none',
  },
  server: {
    // 允许所有 host，解决 "Blocked request" 问题
    allowedHosts: true,
  },
})