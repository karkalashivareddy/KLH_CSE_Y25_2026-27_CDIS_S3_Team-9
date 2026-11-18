import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: { include: ['src/**/*.test.{ts,tsx}'], environment: 'jsdom', setupFiles: './src/test-setup.ts', coverage: { reporter: ['text', 'html'] } },
  server: { port: 5173, strictPort: true, proxy: { '/api': 'http://localhost:8080' } },
})
