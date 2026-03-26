<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/user'
import { User, Lock, Message } from '@element-plus/icons-vue'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (_rule: any, value: string, callback: any) => {
      if (value !== form.password) callback(new Error('两次输入密码不一致'))
      else callback()
    }, trigger: 'blur' }
  ]
}

const handleRegister = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        await register(form)
        ElMessage.success('注册成功，请登录')
        router.push('/login')
      } catch (e: any) {
        ElMessage.error(e.message || '注册失败')
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
        <h2 class="text-3xl font-black tracking-tight text-foreground">加入我们</h2>
        <p class="text-muted-foreground mt-2">开启您的校园时尚之旅</p>
      </div>
      
      <el-form :model="form" :rules="rules" ref="formRef" class="space-y-6">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" class="custom-input" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="电子邮箱" :prefix-icon="Message" size="large" class="custom-input" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="设置密码" :prefix-icon="Lock" show-password size="large" class="custom-input" />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password size="large" class="custom-input" />
        </el-form-item>
        
        <el-button type="primary" class="w-full !h-12 !rounded-xl text-lg font-bold shadow-lg shadow-primary/20" :loading="loading" @click="handleRegister">
          立即注 册
        </el-button>

        <div class="text-sm text-center font-medium">
          <span class="text-muted-foreground">已有账号？</span>
          <router-link to="/login" class="text-primary hover:underline ml-1">返回登录</router-link>
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
