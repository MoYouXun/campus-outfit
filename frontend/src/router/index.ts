import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue')
    },
    {
      path: '/community',
      name: 'community',
      component: () => import('@/views/Community.vue')
    },
    {
      path: '/recommend',
      name: 'recommend',
      component: () => import('@/views/Recommend.vue')
    },
    {
      path: '/ranking',
      name: 'ranking',
      component: () => import('@/views/Ranking.vue')
    },
    {
      path: '/ai-pk',
      name: 'AiPK',
      component: () => import('../views/OutfitPK.vue')
    },
    {
      path: '/ai-try-on',
      name: 'AiTryOn',
      component: () => import('@/views/AiTryOn.vue')
    },
    {
      path: '/profile/:id',
      name: 'profile',
      component: () => import('@/views/Profile.vue')
    },
    {
      path: '/outfit/:id',
      name: 'outfit-detail',
      component: () => import('@/views/OutfitDetail.vue')
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue')
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
      meta: { requiresAdmin: true }
    }
  ]
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const token = userStore.token
  const userInfo = userStore.userInfo
  const role = userInfo ? userInfo.role : null

  // 1. 未登录拦截：非登录/注册页且无 token，强制重定向至 /login
  if (to.path !== '/login' && to.path !== '/register' && !token) {
    next('/login')
    return
  }

  // 2. 已登录防重走：如果已登录还要去登录页，直接跳转首页
  if ((to.path === '/login' || to.path === '/register') && token) {
    next('/')
    return
  }

  // 3. 权限校验
  if (to.meta.requiresAdmin && role !== 'ADMIN') {
    next('/')
    return
  }

  next()
})

export default router