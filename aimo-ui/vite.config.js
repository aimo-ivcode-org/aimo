import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import process from 'node:process'
import path from 'path'
import { fileURLToPath } from 'node:url'

const apiProxyTarget = process.env.AIMO_API_PROXY_TARGET ?? 'http://localhost:8080'
const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'node',
        include: ['src/**/*.test.ts'],
    },
    resolve: {
        alias: {
            '@': path.resolve(__dirname, 'src')
        }
    },
    server: {
        host: true,
        port: 5173,
        strictPort: true,
        proxy: {
            '/aimo-api': {
                target: apiProxyTarget,
                changeOrigin: true,
            }
        }
    },
    build: {
        sourcemap: true
    }
})