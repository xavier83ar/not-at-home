import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// Plain Vitest (no Nuxt environment): components under test import what they use explicitly.
export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '~': fileURLToPath(new URL('./app', import.meta.url)) } },
  test: {
    environment: 'happy-dom',
    include: ['tests/**/*.test.ts'],
  },
})
