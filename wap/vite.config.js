// vite.config.js  —— Production only
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 仅生产配置：无 server/proxy，仅构建优化
export default defineConfig(({ mode }) => {
  // 读取 .env.production 中的变量（例如 VITE_API_BASE / VITE_BASE）
  const env = loadEnv(mode, process.cwd(), '')
  const base = env.VITE_BASE || '/' // 部署非根路径时设置，如 '/wap/'

  return {
    base,
    plugins: [vue()],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    // 构建优化（生产）
    build: {
      target: 'es2018',
      sourcemap: false,            // 如需排查线上问题可改 true
      cssCodeSplit: true,
      minify: 'esbuild',           // 需更强压缩可换 'terser'
      chunkSizeWarningLimit: 1024, // 1MB 提示阈值
      rollupOptions: {
        output: {
          // 常见拆包策略：第三方库/应用代码分离
          manualChunks: (id) => {
            if (id.includes('node_modules')) return 'vendor'
          },
          // hash 命名，避免缓存问题
          entryFileNames: 'assets/[name]-[hash].js',
          chunkFileNames: 'assets/[name]-[hash].js',
          assetFileNames: 'assets/[name]-[hash][extname]',
        },
      },
    },

    // 常量替换（生产态关闭 Vue Devtools）
    define: {
      __VUE_OPTIONS_API__: true,
      __VUE_PROD_DEVTOOLS__: false,
    },

    optimizeDeps: {
      // 仅影响预构建（主要是 dev），这里留着保持一致性
      esbuildOptions: {
        define: {
          __VUE_OPTIONS_API__: 'true',
          __VUE_PROD_DEVTOOLS__: 'false',
        },
      },
    },
  }
})
