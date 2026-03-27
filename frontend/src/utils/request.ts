import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000 // 统一调整为 60s，以适配后端大模型的响应耗时
})

// 请求拦截器
service.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 从 sessionStorage 中获取 token（恢复单标签页会话隔离）
    const token = sessionStorage.getItem('token')
    // 对于不需要认证的接口（如登录和注册），不添加 Authorization 请求头
    const noAuthPaths = ['/api/user/login', '/api/user/register', '/user/login', '/user/register', '/api/weather/now', '/weather/now', '/api/recommend/occasion', '/recommend/occasion', '/api/recommend/season', '/recommend/season']
    const isNoAuthPath = noAuthPaths.some(path => config.url?.includes(path))
    
    if (token && !isNoAuthPath) {
      // 如果 token 存在且不是无需认证的接口，添加到请求头
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: any) => {
    // 处理请求错误
    console.error('Request Error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    
    // 根据项目的实际情况，处理响应数据
    if (res.code !== 200) {
      console.error('Response Error:', res.message)
      
      // 当 token 过期或无效时，跳转到登录页
      if (res.code === 401) {
        sessionStorage.removeItem('token')
        window.location.href = '/login'
      }
      
      return Promise.reject(new Error(res.message || 'Error'))
    } else {
      return res.data
    }
  },
  (error: any) => {
    // 处理响应错误
    console.error('Response Error:', error)
    return Promise.reject(error)
  }
)

export default service