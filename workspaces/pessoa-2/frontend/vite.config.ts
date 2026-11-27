import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'P2_')
  const target = env.P2_API_PROXY ?? (
    mode === 'integration' ? 'http://localhost:8080' : 'http://localhost:8082'
  )

  return {
    base: env.P2_FRONTEND_BASE ?? '/',
    plugins: [vue()],
    server: {
      proxy: {
        '/api': { target, changeOrigin: true },
      },
    },
  }
})
