import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    watch: { ignored: ['**/backend/data/**'] },
    proxy: { '/api': 'http://127.0.0.1:8091' },
  },
})

