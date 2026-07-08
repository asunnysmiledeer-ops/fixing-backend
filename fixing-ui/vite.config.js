import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

// FIX-ING 前端构建配置：
// - dev: /api 前缀代理到本机后端 8080（前后端分离开发）
// - build: 产物直接输出到 fixing-admin 的 static —— 保持"单 jar 本机演示"的部署形态
export default defineConfig({
  plugins: [vue()],
  base: './', // hash 路由 + 相对路径：随便挂哪个路径都能跑
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true, rewrite: p => p.replace(/^\/api/, '') } },
  },
  build: {
    outDir: '../fixing-admin/src/main/resources/static',
    emptyOutDir: true,
  },
})
