import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const integrated = mode === 'integration'
  const proxy = {
    '/api': {
      target: integrated ? 'http://localhost:8080' : 'http://localhost:8083',
      changeOrigin: true
    }
  }

  return {
    plugins: [vue()],
    build: { outDir: integrated ? 'dist-integration' : 'dist' },
    server: { proxy },
    preview: { proxy },
    test: { environment: 'jsdom' }
  }
})
