import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// API 주소는 VITE_API_BASE_URL (기본 http://localhost:18600/api — src/api/client.ts)
export default defineConfig({
  plugins: [react()],
  server: { port: 3010 },
  build: {
    outDir: 'build',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          katex: ['katex'],
        },
      },
    },
  },
});
