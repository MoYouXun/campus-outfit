<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElIcon } from 'element-plus'
import { MagicStick, TrendCharts, Opportunity, UserFilled, User, Setting, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isLoggedIn = computed(() => !!userStore.token)
const username = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '')
const avatar = computed(() => userStore.userInfo?.avatar || '')
const userId = computed(() => userStore.userInfo?.userId)
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const isAuthPage = computed(() => ['/login', '/register', '/admin'].includes(route.path))

const navItems = [
  { label: '社区', path: '/', icon: Opportunity },
  { label: 'AI 穿搭 PK', path: '/ai-pk', icon: TrendCharts },
  { label: '魔法试衣间', path: '/ai-try-on', icon: MagicStick },
  { label: '推荐', path: '/recommend', icon: MagicStick },
  { label: '排行', path: '/ranking', icon: TrendCharts }
]

const logout = () => {
  userStore.clearUser()
  ElMessage.success('已退出登录')
  router.push('/login')
}

// 处理下拉菜单命令，解决点击无反应的问题
const handleCommand = (command: string) => {
  if (command === 'profile') {
    router.push(`/profile/${userId.value}`)
  } else if (command === 'admin') {
    router.push('/admin')
  } else if (command === 'logout') {
    logout()
  }
}

</script>

<template>
  <div id="app" class="flex flex-col min-h-screen bg-background">
    <!-- Navbar -->
    <header v-if="!isAuthPage" class="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <nav class="container mx-auto px-4 h-16 flex items-center justify-between">
        <div class="flex items-center gap-8">
          <router-link to="/" class="flex items-center gap-2 hover:opacity-80 transition-opacity">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-[#1a2a3a] flex-center shadow-lg shadow-primary/20 text-white">
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
            <el-dropdown trigger="click" popper-class="user-dropdown-popper" @command="handleCommand">
              <div class="flex items-center gap-2 cursor-pointer p-1 rounded-full hover:bg-secondary transition-colors">
                <el-avatar :size="32" :src="avatar">
                  <el-icon><UserFilled /></el-icon>
                </el-avatar>
                <span class="text-sm font-bold hidden sm:inline-block pr-2">{{ username }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu class="custom-user-menu">
                  <!-- 菜单操作项 -->
                  <el-dropdown-item command="profile">
                    <el-icon class="menu-icon"><User /></el-icon>
                    <span>个人主页</span>
                  </el-dropdown-item>
                  
                  <el-dropdown-item v-if="isAdmin" command="admin">
                    <el-icon class="menu-icon"><Setting /></el-icon>
                    <span>后台管理</span>
                  </el-dropdown-item>
                  
                  <div class="dropdown-divider"></div>
                  
                  <el-dropdown-item class="logout-item" command="logout">
                    <div class="flex items-center gap-2 w-full h-full">
                      <el-icon class="menu-icon"><SwitchButton /></el-icon>
                      <span>退出登录</span>
                    </div>
                  </el-dropdown-item>
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

/* 用户下拉菜单弹出框样式 */
.el-dropdown__popper.user-dropdown-popper {
  --el-dropdown-menu-box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.15) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  background: rgba(255, 255, 255, 0.8) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 16px !important;
  overflow: hidden;
}

/* 暗色模式下的弹出框样式 */
.dark .el-dropdown__popper.user-dropdown-popper {
  border: 1px solid rgba(255, 255, 255, 0.05) !important;
  background: rgba(15, 23, 42, 0.8) !important;
}

/* 自定义下拉菜单样式 */
.custom-user-menu {
  padding: 8px 0 !important;
  min-width: 220px;
  background: transparent !important;
  border: none !important;
}



/* 分割线样式 */
.dropdown-divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.05);
  margin: 8px 0;
}

/* 暗色模式分割线样式 */
.dark .dropdown-divider {
  background: rgba(255, 255, 255, 0.05);
}

/* 菜单项基础样式 */
.custom-user-menu .el-dropdown-menu__item {
  margin: 2px 8px;
  padding: 10px 16px;
  border-radius: 10px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: all 0.2s ease;
  color: #4b5563 !important;
}

/* 暗色模式菜单项文字样式 */
.dark .custom-user-menu .el-dropdown-menu__item {
  color: #d1d5db !important;
}

/* 菜单项悬停样式 - 已与退出登录效果完全同步 */
.custom-user-menu .el-dropdown-menu__item:hover {
  background: rgba(239, 68, 68, 0.08) !important;
  color: #ef4444 !important;
}


/* 退出登录菜单项悬停样式 */
.custom-user-menu .el-dropdown-menu__item.logout-item:hover {
  background: rgba(239, 68, 68, 0.08) !important;
  color: #ef4444 !important;
}

/* 菜单图标样式 */
.menu-icon {
  font-size: 16px;
}
</style>