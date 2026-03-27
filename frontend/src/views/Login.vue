<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        const res: any = await login(form)
        userStore.setToken(res.token)
        userStore.setUserInfo({ ...res })
        ElMessage.success('欢迎回来！')
        router.push('/')
      } catch (e: any) {
        ElMessage.error(e.message || '登录失败')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<template>
  <div class="min-h-[80vh] flex items-center justify-center p-4">
    <div class="glass-card max-w-md w-full p-10 space-y-8 animate-slide-up">
      <div class="text-center">
        <h2 class="text-3xl font-black tracking-tight text-foreground">登 录</h2>
        <p class="text-muted-foreground mt-2">探索您的专属校园穿搭灵感</p>
      </div>
      
      <el-form :model="form" :rules="rules" ref="formRef" class="space-y-6">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" class="custom-input" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password size="large" class="custom-input" />
        </el-form-item>
        
        <el-button type="primary" class="w-full !h-12 !rounded-xl text-lg font-bold shadow-lg shadow-primary/20" :loading="loading" @click="handleLogin">
          立即登录
        </el-button>

        <div class="text-sm text-center font-medium">
          <span class="text-muted-foreground">还没有账号？</span>
          <router-link to="/register" class="text-primary hover:underline ml-1">立即注册</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.custom-input :deep(.el-input__wrapper) {
  background: rgba(var(--primary-rgb), 0.03);
  box-shadow: none !important;
  border: 1px solid rgba(var(--border-rgb), 0.1);
  border-radius: 12px;
  padding: 8px 16px;
}
.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--el-color-primary);
  background: transparent;
}
</style>
