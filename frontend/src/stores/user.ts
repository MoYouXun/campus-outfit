import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  // 切换为 localStorage 以支持跨标签页同步 (storage event)
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
  const userLocation = ref(JSON.parse(localStorage.getItem('userLocation') || '{"latitude": null, "longitude": null}'))

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
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
    localStorage.setItem('userInfo', JSON.stringify(normalizedInfo))
  }

  const setLocation = (lat: number, lon: number) => {
    userLocation.value = { latitude: lat, longitude: lon }
    localStorage.setItem('userLocation', JSON.stringify(userLocation.value))
  }

  const clearUser = () => {
    token.value = ''
    userInfo.value = {}
    userLocation.value = { latitude: null, longitude: null }
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('userLocation')
    
    // 同时清理 sessionStorage 以防万一
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
  }

  return { token, userInfo, userLocation, setToken, setUserInfo, setLocation, clearUser }
})
