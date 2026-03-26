import { defineStore } from 'pinia'

export const useMainStore = defineStore('main', {
  state: () => ({
    // 可以在这里定义全局状态
    token: localStorage.getItem('token') || '',
    userInfo: null
  }),
  getters: {
    // 可以在这里定义计算属性
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    // 可以在这里定义方法
    setToken(token: string) {
      this.token = token
      localStorage.setItem('token', token)
    },
    removeToken() {
      this.token = ''
      localStorage.removeItem('token')
    },
    setUserInfo(userInfo: any) {
      this.userInfo = userInfo
    }
  }
})