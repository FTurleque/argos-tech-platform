import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// Tests de composants des vues (Vitest) : DOM simulé par happy-dom, réseau simulé par chaque test. Les tests
// `node --test` (tests/**/*.test.mjs) restent hors de cette configuration.
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    include: ['tests/vues/**/*.spec.ts'],
    setupFiles: ['tests/vues/setup.ts'],
    restoreMocks: true,
    unstubGlobals: true,
  },
})
