<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Edit, InfoFilled, Checked } from '@element-plus/icons-vue'
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
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-bold flex items-center gap-2">
          <el-icon class="text-primary"><Edit /></el-icon>
          个人资料设置
        </h3>
        <el-button link @click="visible = false">Close</el-button>
      </div>
    </template>

    <div v-loading="loading" class="px-2 pt-2 pb-6 overflow-x-hidden">
      <!-- 头像区域 -->
      <div class="flex flex-col items-center mb-8">
        <div class="relative">
          <el-avatar 
            :size="100" 
            :src="form.avatar" 
            class="border-4 border-white shadow-xl bg-secondary/20"
          />
          <div class="absolute -bottom-1 -right-1 z-10 transition-transform hover:scale-110 active:scale-95">
             <UploadImage variant="compact" :max-width="400" @upload-success="handleAvatarSuccess" />
          </div>
        </div>
        <p class="text-[10px] text-muted-foreground mt-3 uppercase tracking-widest font-black opacity-60">点击右下角按钮更换头像</p>
      </div>

      <el-form label-position="top" class="custom-form">
        <div class="grid grid-cols-1 gap-4">
          <el-form-item label="昵称">
            <template #label><span class="inline-flex items-center gap-1"><el-icon><User /></el-icon> 展示昵称</span></template>
            <el-input v-model="form.nickname" placeholder="给你的穿搭一个响亮的号头" maxlength="20" show-word-limit />
          </el-form-item>

          <el-form-item label="性别">
            <el-radio-group v-model="form.gender" class="flex w-full bg-secondary/30 p-1 rounded-xl">
              <el-radio-button :label="1" class="flex-1">男生</el-radio-button>
              <el-radio-button :label="2" class="flex-1">女生</el-radio-button>
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
      <div class="flex gap-3 px-2">
        <el-button @click="visible = false" round class="flex-1">取 消</el-button>
        <el-button 
          type="primary" 
          @click="submit" 
          :loading="loading" 
          round 
          class="flex-1 shadow-lg shadow-primary/30"
        >
          保存设置
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.profile-update-dialog :deep(.el-dialog) {
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
}

.profile-update-dialog :deep(.el-form--label-top .el-form-item__label) {
  font-weight: 700;
  font-size: 0.8rem;
  color: #64748b;
  margin-bottom: 4px;
  padding: 0;
}

.profile-update-dialog :deep(.el-input__wrapper),
.profile-update-dialog :deep(.el-textarea__inner) {
  border-radius: 12px;
  box-shadow: none !important;
  border: 1px solid #e2e8f0;
  transition: all 0.2s;
  background-color: white;
}

.profile-update-dialog :deep(.el-input__wrapper:hover),
.profile-update-dialog :deep(.el-textarea__inner:hover) {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.profile-update-dialog :deep(.el-radio-button__inner) {
  border: none !important;
  background: transparent !important;
  border-radius: 10px !important;
  color: #64748b;
  font-size: 0.8rem;
  font-weight: bold;
  padding: 8px 24px;
}

.profile-update-dialog :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: white !important;
  color: var(--el-color-primary) !important;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1) !important;
}
</style>
