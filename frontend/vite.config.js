import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import net from 'node:net'

// 看门狗插件：后端（8080）曾上线后又连续不可达时，自动退出 Vite 开发服务器。
// 配合「关闭浏览器标签页 → 插件调用后端优雅退出」，实现 Java + Node 一起自动释放，
// 避免下次启动时 8080 / 5173 端口被残留进程占用。
function backendWatchdog() {
  let seenUp = false
  let downStreak = 0
  const INTERVAL_MS = 3000
  const MAX_DOWN_STREAK = 2 // 连续 2 次（约 6 秒）不可达才退出，避免瞬时抖动误杀

  const check = () => {
    const sock = net.connect(8080, '127.0.0.1')
    sock.setTimeout(2000)
    sock.on('connect', () => { seenUp = true; downStreak = 0; sock.destroy() })
    sock.on('timeout', () => { sock.destroy(); onDown() })
    sock.on('error', () => { onDown() })
    function onDown() {
      if (!seenUp) return // 启动阶段后端尚未就绪，保持等待，不退出
      downStreak++
      if (downStreak >= MAX_DOWN_STREAK) {
        console.log('[watchdog] 检测到后端已关闭，Vite 自动退出')
        process.exit(0)
      }
    }
  }

  return {
    name: 'backend-watchdog',
    configureServer() {
      setInterval(check, INTERVAL_MS)
    }
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), backendWatchdog()],
  server: {
    port: 5173,
    // 解决跨域：将 /api 请求代理到后端 8080 端口
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
