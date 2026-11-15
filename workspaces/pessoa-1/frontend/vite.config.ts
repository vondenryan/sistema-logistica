import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
export default defineConfig({ plugins: [vue()], server: { port: 5173, strictPort: true, proxy: { '/api': 'http://localhost:8080' } }, test: { environment: 'jsdom', restoreMocks: true } })
