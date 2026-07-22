import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client reference `global` ; on le mappe sur globalThis au build (remplace l'ancien
  // shim runtime `window.global = window` de main.jsx).
  define: {
    global: 'globalThis',
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
      },
      // WebSocket STOMP/SockJS : proxifie /ws vers l'API (ws:true pour l'upgrade WebSocket).
      '/ws': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      // Si une redirection relative /oauth2/... atteint le navigateur sur le port du front, on proxifie vers l'API.
      '/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
      },
      '/login/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
      },
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return

          if (id.includes('react-router-dom') || id.includes('react-router')) return 'vendor-router'
          if (id.includes('framer-motion')) return 'vendor-motion'
          if (id.includes('i18next') || id.includes('react-i18next')) return 'vendor-i18n'
          if (id.includes('lucide-react')) return 'vendor-icons'
          if (id.includes('lottie-react') || id.includes('lottie-web')) return 'vendor-lottie'

          return 'vendor'
        },
      },
    },
  },
})
