import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  // 恢复为 sessionStorage 以实现单标签页会话隔离
  const token = ref(sessionStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(sessionStorage.getItem('userInfo') || '{}'))
  const userLocation = ref(JSON.parse(sessionStorage.getItem('userLocation') || '{"latitude": null, "longitude": null}'))

  const setToken = (newToken: string) => {
    token.value = newToken
    sessionStorage.setItem('token', newToken)
  }

  const setUserInfo = (info: any) => {
    // 归一化 ID 字段，防止 id/userId 混用导致状态丢失
    const normalizedId = info.id || info.userId
    const normalizedInfo = { 
      ...info, 
      id: normalizedId, 
      userId: normalizedId 
    }
    userInfo.value = normalizedInfo
    sessionStorage.setItem('userInfo', JSON.stringify(normalizedInfo))
  }

  const setLocation = (lat: number, lon: number) => {
    userLocation.value = { latitude: lat, longitude: lon }
    sessionStorage.setItem('userLocation', JSON.stringify(userLocation.value))
  }

  const clearUser = () => {
    token.value = ''
    userInfo.value = {}
    userLocation.value = { latitude: null, longitude: null }
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
    sessionStorage.removeItem('userLocation')
    
    // 防御性清除 localStorage
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, userLocation, setToken, setUserInfo, setLocation, clearUser }
})
