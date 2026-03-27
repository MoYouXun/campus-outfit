import { createRouter, createWebHistory } from 'vue-router'

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

router.beforeEach((to, _from, next) => {
  const token = sessionStorage.getItem('token')
  const userInfoStr = sessionStorage.getItem('userInfo')
  const role = userInfoStr ? JSON.parse(userInfoStr).role : null

  // 未登录用户，非登录/注册页则跳转至 /login
  if (to.path !== '/login' && to.path !== '/register' && !token) {
    next('/login')
    return
  }

  // 需要管理员权限的路由，角色不符则跳转首页
  if (to.meta.requiresAdmin && role !== 'ADMIN') {
    next('/')
    return
  }

  next()
})

export default router