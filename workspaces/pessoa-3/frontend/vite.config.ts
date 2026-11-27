import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: mode === 'integration' ? 'http://localhost:8080' : 'http://localhost:8083',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom'
  }
}))
