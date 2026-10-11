import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      // 运营接口代理到本地 awn-api
      '/api/v1/admin': {
        target: process.env.AWN_API_BASE ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
