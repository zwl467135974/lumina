import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api/v1/agents': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api/v1/conversations': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api/v1/knowledge': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api/v1/tools': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api/v1/files': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api/v1/workflows': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue']
        }
      }
    }
  }
})
