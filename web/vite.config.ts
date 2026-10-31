import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  // `npm run dev` serves the UI on :5173 against the control app on :8080.
  server: { proxy: { '/api': 'http://localhost:8080' } },
  // Monaco is large and bundled locally on purpose: production networks cannot reach a CDN.
  build: { chunkSizeWarningLimit: 8000 },
});
