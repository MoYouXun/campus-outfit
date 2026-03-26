import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

// 注册Element Plus和图标
app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 全局注册所有Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 添加警告处理器，过滤掉Element Plus无限滚动指令的废弃警告
app.config.warnHandler = (msg, instance, trace) => {
  // 忽略Element Plus无限滚动指令的废弃警告
  if (msg.includes('[ElInfiniteScroll] [API] the directive v-infinite-scroll is about to be deprecated')) {
    return
  }
  // 其他警告正常显示
  console.warn(msg, instance, trace)
}

app.mount('#app')