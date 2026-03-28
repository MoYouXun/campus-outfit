<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getUserInfo, followUser, unfollowUser } from '@/api/user'
import { getMyOutfits, getUserOutfits, deleteOutfit, getMyPrivateOutfits, updateOutfitStatus } from '@/api/outfit'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh } from '@element-plus/icons-vue'
import { aiTryOn } from '@/api/ai'
import UploadImage from '@/components/UploadImage.vue'

const route = useRoute()
const userId = route.params.id as string
const user = ref<any>(null)
const outfits = ref<any[]>([])
const isFollowing = ref(false)
const userStore = useUserStore()
const isCurrentUser = ref(false)
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)
const privateOutfits = ref<any[]>([])
const activeTab = ref('public')

const loadUser = async () => {
  try {
    const res: any = await getUserInfo(userId, currentUserId.value)
    user.value = res.user
    isFollowing.value = res.following // Jackson 序列化 isFollowing 为 following
    
    // 检查是否是当前用户
    isCurrentUser.value = user.value && currentUserId.value && Number(user.value.id) === Number(currentUserId.value)
    
    // 加载该用户的穿搭：本人展示全部（含私密），他人仅展示公开
    let outfitsRes: any
    if (isCurrentUser.value) {
      outfitsRes = await getMyOutfits({ page: 1, size: 20 })
      // 同步获取私人衣橱数据
      const privateRes: any = await getMyPrivateOutfits()
      privateOutfits.value = privateRes || []
    } else {
      outfitsRes = await getUserOutfits({ 
        userId: userId,
        page: 1, 
        size: 20 
      })
    }
    outfits.value = outfitsRes.records || []
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (id: number | string) => {
  try {
    await deleteOutfit(id)
    ElMessage.success('帖子删除成功')
    loadUser()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '帖子删除失败')
  }
}

const handleToggleStatus = async (item: any, newStatus: string) => {
  const actionText = newStatus === 'PRIVATE' ? '移入私人衣橱' : '发布到社区'
  try {
    await ElMessageBox.confirm(`确定要将该穿搭${actionText}吗？`, '状态控制', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
      customClass: 'glass-message-box'
    })
    
    await updateOutfitStatus(item.id, newStatus as any)
    ElMessage.success(`${actionText}成功`)
    
    // 无刷新同步逻辑
    if (newStatus === 'PRIVATE') {
      const idx = outfits.value.findIndex(o => o.id === item.id)
      if (idx > -1) outfits.value.splice(idx, 1)
      privateOutfits.value.unshift({ ...item, status: 'PRIVATE' })
    } else {
      const idx = privateOutfits.value.findIndex(o => o.id === item.id)
      if (idx > -1) privateOutfits.value.splice(idx, 1)
      outfits.value.unshift({ ...item, status: 'PUBLISHED' })
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

const handleFollow = async () => {
  try {
    if (isFollowing.value) await unfollowUser(userId)
    else await followUser(userId)
    isFollowing.value = !isFollowing.value
    loadUser()
  } catch (e) {}
}

const showTryOnDialog = ref(false)
const tryOnLoading = ref(false)
const personImageUrl = ref('') // 空图，待上传
const selectedOutfitUrl = ref('')
const tryOnResultUrl = ref('')

const handleAvatarUploadSuccess = (data: any) => {
  personImageUrl.value = data.base64Data
}

const handleTryOn = async () => {
  if (!personImageUrl.value || !selectedOutfitUrl.value) {
    ElMessage.warning('请提供人像照片并选择一件衣服')
    return
  }
  tryOnLoading.value = true
  tryOnResultUrl.value = ''
  try {
    const res: any = await aiTryOn({
      humanImageUrl: personImageUrl.value,
      garmentImageUrl: selectedOutfitUrl.value
    })
    tryOnResultUrl.value = res.data || res || personImageUrl.value
    ElMessage.success('魔法换装成功！')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '换装请求失败')
  } finally {
    tryOnLoading.value = false
  }
}

onMounted(loadUser)
</script>

<template>
  <div class="min-h-screen bg-background pb-20">
    <div class="h-48 bg-gradient-to-r from-primary/20 to-secondary/20"></div>
    <div class="max-w-5xl mx-auto px-6 -mt-16">
      <div class="flex flex-col md:flex-row items-center md:items-end gap-6 mb-10">
        <el-avatar :size="120" :src="user?.avatar" class="border-4 border-white shadow-xl" />
        <div class="flex-1 text-center md:text-left">
          <h1 class="text-3xl font-bold mb-2">{{ user?.username }}</h1>
          <p class="text-muted-foreground mb-4">{{ user?.bio || '这家伙很懒，什么都没留下~' }}</p>
          <div class="flex gap-6 justify-center md:justify-start">
            <div class="text-center"><div class="font-bold text-xl">{{ user?.followCount || 0 }}</div><div class="text-xs text-muted-foreground uppercase">关注</div></div>
            <div class="text-center"><div class="font-bold text-xl">{{ user?.fanCount || 0 }}</div><div class="text-xs text-muted-foreground uppercase">粉丝</div></div>
            <div class="text-center"><div class="font-bold text-xl">{{ outfits.length }}</div><div class="text-xs text-muted-foreground uppercase">穿搭</div></div>
          </div>
        </div>
        <div class="pb-2 flex gap-2">
          <el-button v-if="isCurrentUser" type="primary" :icon="MagicStick" @click="showTryOnDialog = true" plain round>
            AI 换装室
          </el-button>
          <el-button v-if="!isCurrentUser" :type="isFollowing ? 'default' : 'primary'" @click="handleFollow" size="large" round>
            {{ isFollowing ? '取消关注' : '加关注' }}
          </el-button>
        </div>
      </div>

      <div class="mt-8">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <el-tab-pane label="公开穿搭" name="public">
            <MasonryGallery :outfits="outfits" @delete="handleDelete" @toggle-status="(item) => handleToggleStatus(item, 'PRIVATE')" />
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="私人衣橱" name="private">
            <div class="pt-2">
              <el-alert title="这是您的私密空间，此页签下所有内容仅您本人可见。" type="warning" show-icon class="mb-6" :closable="false" />
              <MasonryGallery :outfits="privateOutfits" @delete="handleDelete" @toggle-status="(item) => handleToggleStatus(item, 'PUBLISHED')" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- AI 换装室弹窗 -->
    <el-dialog v-model="showTryOnDialog" title="🪄 私人 AI 换装室" width="600px" destroy-on-close>
      <div v-loading="tryOnLoading" element-loading-text="AI 正在为您施展换装魔法..." class="p-4">
        <el-alert title="作为概念原型，目前换装功能基于图生图接口，提供效果预览。" type="info" show-icon class="mb-4" :closable="false"/>
        
        <div class="mb-4">
          <div class="text-sm font-bold mb-3 text-foreground/80 flex items-center gap-2">1. 个人基础照 (人像)</div>
          
          <!-- 完全复用首页炫酷上传组件 -->
          <div v-if="!personImageUrl" class="scale-90 origin-top">
            <UploadImage @upload-success="handleAvatarUploadSuccess" />
          </div>
          
          <div v-else class="relative w-40 h-56 rounded-xl mx-auto border border-border shadow-sm overflow-hidden bg-secondary/30 group">
            <img :src="personImageUrl" class="w-full h-full object-cover" />
            <div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white text-sm gap-1 font-medium cursor-pointer" @click="personImageUrl = ''">
              <el-icon><Refresh /></el-icon> 更换照片
            </div>
          </div>
        </div>

        <div class="mb-6">
          <div class="text-sm font-bold mb-2 text-foreground/80">2. 选择衣橱服饰 (<span class="text-primary italic">将衣服穿在底图上</span>)</div>
          <el-select v-model="selectedOutfitUrl" placeholder="从您发布的穿搭中选择一件上装或下装..." class="w-full" size="large">
            <el-option
              v-for="outfit in outfits"
              :key="outfit.id"
              :label="outfit.title"
              :value="outfit.thumbnailUrl || outfit.imageUrls?.[0]"
            >
              <div class="flex items-center gap-3">
                <img :src="outfit.thumbnailUrl || outfit.imageUrls?.[0]" class="w-8 h-8 rounded-md object-cover shadow-sm border border-border" />
                <span class="font-medium">{{ outfit.title }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="text-center mt-8">
          <el-button type="primary" :icon="MagicStick" @click="handleTryOn" :disabled="!personImageUrl || !selectedOutfitUrl" class="w-full h-12 text-lg rounded-xl shadow-lg shadow-primary/30 transition-transform hover:-translate-y-1">
            一 键 换 装
          </el-button>
        </div>

        <div v-if="tryOnResultUrl" class="mt-8 animate-fade-in">
          <el-divider>✨ 魔法换装结果 ✨</el-divider>
          <div class="w-full rounded-2xl overflow-hidden shadow-2xl border border-primary/30 p-2 bg-gradient-to-br from-primary/10 via-background to-secondary/10">
            <el-image :src="tryOnResultUrl" class="w-full rounded-xl mix-blend-multiply dark:mix-blend-normal" :preview-src-list="[tryOnResultUrl]" fit="contain" />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
