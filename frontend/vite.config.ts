/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // `npm run dev`: forward API calls (including the SSE stream) to a locally running backend,
    // so the browser only ever talks to one origin - the same setup nginx provides in Docker.
    proxy: {
      '/api': 'http://localhost:8080',
    },
    fs: {
      // tests import the backend's contract samples from ../contracts
      allow: ['..'],
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // No test may touch the network: unhandled requests fail (see setup.ts).
  },
})
