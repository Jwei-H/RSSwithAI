import path from 'node:path'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: false,
      includeAssets: ['rss.svg', 'vite.svg'],
      manifest: {
        name: 'RSSwithAI',
        short_name: 'RSSwithAI',
        description: 'AI 增强的 RSS 阅读器',
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/rss.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any',
          },
          {
            src: '/vite.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        navigateFallback: 'index.html',
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
      },
    }),
  ],
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