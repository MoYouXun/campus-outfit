<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { computed, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElIcon } from 'element-plus'
import { MagicStick, TrendCharts, Opportunity, Sunny, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const handleStorageChange = (event: StorageEvent) => {
  if (event.key === 'token') {
    // 检测到其他标签页 Token 变化，强制刷新以同步状态
    window.location.reload()
  }
}

onMounted(() => {
  window.addEventListener('storage', handleStorageChange)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
})

const isLoggedIn = computed(() => !!userStore.token)
const username = computed(() => userStore.userInfo?.username || '')
const avatar = computed(() => userStore.userInfo?.avatar || '')
const userId = computed(() => userStore.userInfo?.userId)
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))

const navItems = [
  { label: '首页', path: '/', icon: Sunny },
  { label: '社区', path: '/community', icon: Opportunity },
  { label: '推荐', path: '/recommend', icon: MagicStick },
  { label: '排行', path: '/ranking', icon: TrendCharts }
]

const logout = () => {
  userStore.clearUser()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div id="app" class="flex flex-col min-h-screen bg-background">
    <!-- Navbar -->
    <header v-if="!isAuthPage" class="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <nav class="container mx-auto px-4 h-16 flex items-center justify-between">
        <div class="flex items-center gap-8">
          <router-link to="/" class="flex items-center gap-2 hover:opacity-80 transition-opacity">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-indigo-600 flex-center shadow-lg shadow-primary/20 text-white">
              <el-icon size="24"><MagicStick /></el-icon>
            </div>
            <span class="text-xl font-black tracking-tighter hidden sm:inline-block">Campus<span class="text-primary">Outfit</span></span>
          </router-link>
          
          <div class="hidden md:flex items-center gap-1">
            <router-link v-for="item in navItems" :key="item.path" :to="item.path" 
              class="px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200"
              :class="[route.path === item.path ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-secondary hover:text-foreground']">
              {{ item.label }}
            </router-link>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <template v-if="isLoggedIn">
            <el-dropdown trigger="click">
              <div class="flex items-center gap-2 cursor-pointer p-1 rounded-full hover:bg-secondary transition-colors">
                <el-avatar :size="32" :src="avatar">
                  <el-icon><UserFilled /></el-icon>
                </el-avatar>
                <span class="text-sm font-bold hidden sm:inline-block pr-2">{{ username }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push(`/profile/${userId}`)">个人主页</el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" @click="router.push('/admin')">后台管理</el-dropdown-item>
                  <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button @click="router.push('/login')" round>登录</el-button>
            <el-button type="primary" @click="router.push('/register')" round>注册</el-button>
          </template>
        </div>
      </nav>
    </header>

    <!-- Main Content -->
    <main class="flex-1">
      <RouterView v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>

    <footer v-if="!isAuthPage" class="border-t py-6 bg-secondary/50">
      <div class="container mx-auto px-4 text-center text-sm text-muted-foreground">
        © 2026 Campus Outfit. 面向校园的智能穿搭与潮流社区。
      </div>
    </footer>
  </div>
</template>

<style>
.page-enter-active, .page-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>