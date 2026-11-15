import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Le même build sert au portail web et au client desktop Electron (ADR-0022).
export default defineConfig({
  plugins: [vue()],
  // Chemins relatifs : le build fonctionne servi par un serveur web ou chargé par le desktop.
  base: './',
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    target: 'es2022',
    sourcemap: false,
  },
})
