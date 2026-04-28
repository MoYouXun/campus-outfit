<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Edit, InfoFilled, Checked, Close, Camera } from '@element-plus/icons-vue'
import { updateProfile, getUserInfo } from '@/api/user'
import UploadImage from '@/components/UploadImage.vue'

const visible = ref(false)
const loading = ref(false)
const emit = defineEmits(['success'])

const form = reactive({
  nickname: '',
  avatar: '',
  bio: '',
  gender: '',
  oldPassword: '',
  newPassword: ''
})

// 计算属性：判断密码是否发生变更
const isPasswordChanged = computed(() => !!(form.newPassword && form.newPassword.trim()))

/**
 * 打开弹窗并加载用户信息
 * @param userId 用户 ID
 */
const open = async (userId: string | number) => {
  visible.value = true
  form.oldPassword = ''
  form.newPassword = ''
  
  loading.value = true
  try {
    const res: any = await getUserInfo(userId)
    const user = res.user || res
    form.nickname = user.nickname || ''
    form.avatar = user.avatar || ''
    form.bio = user.bio || ''
    form.gender = user.gender
  } catch (err: any) {
    console.error('[ProfileDialog] 获取用户信息异常:', err)
    ElMessage.error(err.response?.data?.message || '获取用户信息失败')
  } finally {
    loading.value = false
  }
}

const handleAvatarSuccess = (data: any) => {
  form.avatar = data.url || data.base64Data
}

/**
 * 提交资料修改
 */
const submit = async () => {
  loading.value = true
  try {
    await updateProfile(form)
    ElMessage.success('资料修改成功')
    visible.value = false
    emit('success', { passwordChanged: isPasswordChanged.value })
  } catch (err: any) {
    console.error('[ProfileDialog] 修改资料异常:', err)
    ElMessage.error(err.response?.data?.message || '资料修改失败')
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog 
    v-model="visible" 
    title="修改个人资料" 
    width="480px" 
    destroy-on-close 
    class="profile-update-dialog"
    :show-close="false"
  >
    <template #header>
      <div class="flex items-center justify-between pb-2">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-primary/10 flex-center">
            <el-icon class="text-primary" size="20"><Edit /></el-icon>
          </div>
          <div>
            <h3 class="text-xl font-bold text-foreground">个人资料设置</h3>
            <p class="text-xs text-muted-foreground">定制属于您的校园时尚主页</p>
          </div>
        </div>
        <el-button 
          circle 
          @click="visible = false" 
          class="hover:bg-secondary/50 border-none bg-transparent"
        >
          <el-icon size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="px-2 pt-2 pb-6 overflow-x-hidden">
      <!-- 头像上传区域 -->
      <div class="flex flex-col items-center mb-10 group">
        <div class="relative">
          <!-- 动态背景圈 -->
          <div class="absolute -inset-2 bg-gradient-to-tr from-primary/20 to-secondary/20 rounded-full blur-md group-hover:blur-lg transition-all duration-500"></div>
          
          <div class="relative overflow-hidden rounded-full border-4 border-white shadow-2xl">
            <el-avatar 
              :size="120" 
              :src="form.avatar" 
              class="bg-secondary/20 transition-transform duration-700 group-hover:scale-110"
            />
            <!-- 悬停遮罩 -->
            <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex-center">
               <el-icon class="text-white" size="24"><Camera /></el-icon>
            </div>
          </div>

          <!-- 悬浮上传按钮 -->
          <div class="absolute bottom-1 right-1 z-20">
             <UploadImage variant="compact" :max-width="400" @upload-success="handleAvatarSuccess" />
          </div>
        </div>
        <p class="text-xs font-bold text-primary/60 mt-4 tracking-wider uppercase">点击头像或右下角更换</p>
      </div>

      <el-form label-position="top" class="custom-form">
        <div class="grid grid-cols-1 gap-4">
          <el-form-item label="昵称">
            <template #label><span class="inline-flex items-center gap-1"><el-icon><User /></el-icon> 展示昵称</span></template>
            <el-input v-model="form.nickname" placeholder="给你的穿搭一个响亮的号头" maxlength="20" show-word-limit />
          </el-form-item>

          <el-form-item label="性别">
            <el-radio-group v-model="form.gender" class="flex w-full bg-secondary/30 p-1 rounded-xl">
              <el-radio-button :value="1" class="flex-1">男生</el-radio-button>
              <el-radio-button :value="2" class="flex-1">女生</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="个人简介">
            <template #label><span class="inline-flex items-center gap-1"><el-icon><InfoFilled /></el-icon> 穿搭哲学 (简介)</span></template>
            <el-input 
              v-model="form.bio" 
              type="textarea" 
              :rows="3" 
              placeholder="分享你的穿搭理念或者心情..." 
              maxlength="100" 
              show-word-limit 
              class="rounded-xl overflow-hidden"
            />
          </el-form-item>

          <div class="mt-4 p-4 rounded-2xl bg-primary/5 border border-primary/10 shadow-inner">
            <h4 class="text-xs font-black text-primary/60 mb-4 flex items-center gap-2 uppercase tracking-wide">
              <el-icon><Checked /></el-icon> 安全认证 (修改密码时填写)
            </h4>
            
            <div class="space-y-4">
              <el-form-item label="当前密码" class="mb-0">
                <el-input v-model="form.oldPassword" type="password" placeholder="请输入原密码" show-password :prefix-icon="Lock" />
              </el-form-item>
              <el-form-item label="新密码" class="mb-0">
                <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码" show-password :prefix-icon="Lock" />
              </el-form-item>
            </div>
          </div>
        </div>
      </el-form>
    </div>

    <template #footer>
      <div class="flex gap-4 p-4 bg-secondary/5 border-t border-border/50">
        <el-button @click="visible = false" round size="large" class="flex-1 font-bold">
          取 消
        </el-button>
        <el-button 
          type="primary" 
          @click="submit" 
          :loading="loading" 
          round 
          size="large"
          class="flex-1 premium-submit-btn shadow-xl shadow-primary/20 font-bold"
        >
          保存所有设置
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.profile-update-dialog :deep(.el-dialog) {
  border-radius: 32px;
  overflow: hidden;
  box-shadow: 0 40px 80px -20px rgba(0, 0, 0, 0.3);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  margin-top: 8vh !important;
}

.profile-update-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px 24px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.profile-update-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.profile-update-dialog :deep(.el-form--label-top .el-form-item__label) {
  font-weight: 800;
  font-size: 0.75rem;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 8px;
}

.profile-update-dialog :deep(.el-input__wrapper),
.profile-update-dialog :deep(.el-textarea__inner) {
  border-radius: 16px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02) inset !important;
  border: 1px solid #e2e8f0;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background-color: rgba(255, 255, 255, 0.5);
  padding: 8px 12px;
}

.profile-update-dialog :deep(.el-input__wrapper.is-focus),
.profile-update-dialog :deep(.el-textarea__inner:focus) {
  border-color: var(--el-color-primary);
  background-color: white;
  box-shadow: 0 0 0 4px var(--el-color-primary-light-8) !important;
  transform: translateY(-1px);
}

.profile-update-dialog :deep(.el-radio-button__inner) {
  border: none !important;
  background: transparent !important;
  border-radius: 12px !important;
  color: #64748b;
  font-size: 0.85rem;
  font-weight: 700;
  padding: 10px 0;
  transition: all 0.3s;
}

.profile-update-dialog :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: white !important;
  color: var(--el-color-primary) !important;
  box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.15) !important;
  transform: scale(1.02);
}

.premium-submit-btn {
  background: var(--el-color-primary) !important;
  border: none !important;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) !important;
}

.premium-submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px -8px var(--el-color-primary) !important;
}

.premium-submit-btn:active {
  transform: scale(0.98);
}

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

@keyframes slide-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.custom-form {
  animation: slide-in 0.5s ease-out forwards;
}
</style>
