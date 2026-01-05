import axios from 'axios'
import { useAuth } from '@/store/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'https://java.ise-app.xyz/api',
  // baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8866',
  withCredentials: true,   // 让浏览器携带/接收 httpOnly 的 refresh_token
  timeout: 20000
})

let isRefreshing = false
let queue = []

function runQueue (newToken, err) {
  queue.forEach(({ resolve, reject, config }) => {
    if (newToken) {
      config._retry = true
      config.headers.Authorization = `Bearer ${newToken}`
      resolve(api(config))
    } else {
      reject(err)
    }
  })
  queue = []
}

// 请求拦截器：自动带上 access token
api.interceptors.request.use((config) => {
  const auth = useAuth()
  if (auth.token) {
    config.headers['Authorization'] = `Bearer ${auth.token}`
  }
  return config
})

// 响应拦截器：处理 401
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const { response, config } = error || {}
    if (!response) throw error

    const url = config?.url || ''
    const tokenStatus = (response.headers && response.headers['x-token-status']) || ''

    // 非 401、已重试、或自身是登录/刷新接口 -> 直接抛出
    if (
      response.status !== 401 ||
      config._retry ||
      url.includes('/auth/login') ||
      url.includes('/auth/refresh')
    ) {
      throw error
    }

    const auth = useAuth()

    // 只有 TOKEN_EXPIRED 才尝试刷新；其它一律登出
    if (tokenStatus !== 'TOKEN_EXPIRED') {
      auth.clear()
      window.location.href = '/login?expired=1'
      throw error
    }

    // 如果正在刷新，挂起当前请求
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        queue.push({ resolve, reject, config })
      })
    }

    // 开始刷新
    isRefreshing = true
    try {
      // 注意：后端 ApiResp 结构为 { code:0, data:{ token: '...' } }
      const { data } = await api.post('/auth/refresh', {}) // withCredentials 已在实例上打开
      if (data?.code === 0 && data?.data?.token) {
        const newToken = data.data.token
        auth.setAccessToken(newToken)

        // 唤醒等待队列
        runQueue(newToken, null)

        // 重放当前请求
        config._retry = true
        config.headers.Authorization = `Bearer ${newToken}`
        return api(config)
      }

      // 刷新失败
      auth.clear()
      runQueue(null, error)
      window.location.href = '/login?expired=1'
      throw error
    } catch (e) {
      auth.clear()
      runQueue(null, e)
      window.location.href = '/login?expired=1'
      throw e
    } finally {
      isRefreshing = false
    }
  }
)

export default api
