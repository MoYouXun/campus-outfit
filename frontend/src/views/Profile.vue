<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserInfo, followUser, unfollowUser } from '@/api/user'
import { getMyOutfits, getUserOutfits, deleteOutfit, getMyPrivateOutfits, updateOutfitStatus, getFavoriteOutfits } from '@/api/outfit'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '@/api/interaction'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Delete, Message } from '@element-plus/icons-vue'
import { getWardrobeList, uploadWardrobeItem, deleteWardrobeItem } from '@/api/wardrobe'
import EditProfileDialog from '@/components/EditProfileDialog.vue'
const editDialogRef = ref<any>(null)
const router = useRouter()

const handleEditSuccess = (data: { passwordChanged: boolean }) => {
  if (data.passwordChanged) {
    ElMessage.success('密码已修改，请重新登录')
    userStore.clearUser()
    router.push('/login')
  } else {
    // 仅修改了基础资料，无需重新登录，直接刷新当前页面数据
    loadUser()
    // 同步更新全局 Store 中的用户信息（用于同步顶部导航栏等组件）
    getUserInfo(userId, currentUserId.value).then((res: any) => {
      userStore.setUserInfo(res.user)
    })
    ElMessage.success('个人资料已更新')
  }
}

const route = useRoute()
const userId = route.params.id as string
const user = ref<any>(null)
const outfits = ref<any[]>([]) // 公开列表
const privateOutfits = ref<any[]>([]) // 私人衣橱
const favoriteOutfits = ref<any[]>([]) // 我的收藏
const wardrobeItems = ref<any[]>([]) // 我的衣柜
const wardrobeLoading = ref(false)
const isUploading = ref(false)
const isFollowing = ref(false)
const userStore = useUserStore()
const isCurrentUser = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)
const activeTab = ref('public')

const loadWardrobe = async () => {
  if (!isCurrentUser.value) return
  wardrobeLoading.value = true
  try {
    const res: any = await getWardrobeList()
    wardrobeItems.value = res || []
  } catch (e) {
    console.error('获取衣柜失败', e)
  } finally {
    wardrobeLoading.value = false
  }
}

const handleWardrobeBatchUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return

  const files = Array.from(input.files)
  isUploading.value = true
  
  try {
    for (const file of files) {
      try {
        await uploadWardrobeItem(file)
        console.log(`单品 ${file.name} 上传并识别完成`)
      } catch (e: any) {
        // 单个文件识别失败（如非单品逻辑拦截），继续处理下一个
        console.error(`单品 ${file.name} 处理失败:`, e)
      }
    }
    ElMessage.success('批量处理完成')
    loadWardrobe() // 统一刷新一次
  } finally {
    isUploading.value = false
    input.value = '' // 重置 input 以便下次选择同一张图
  }
}

const handleWardrobeDelete = async (id: number | string) => {
  try {
    await ElMessageBox.confirm('确定要从您的电子衣橱中永久移除此单品吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'glass-message-box'
    })
    
    await deleteWardrobeItem(id)
    ElMessage.success('单品已移除')
    loadWardrobe()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '移除失败')
    }
  }
}

const loadUser = async () => {
  try {
    const res: any = await getUserInfo(userId, currentUserId.value)
    user.value = res.user
    isFollowing.value = res.following
    
    isCurrentUser.value = user.value && currentUserId.value && Number(user.value.id) === Number(currentUserId.value)
    
    let outfitsRes: any
    if (isCurrentUser.value) {
      outfitsRes = await getMyOutfits({ page: 1, size: 20 })
      const privateRes: any = await getMyPrivateOutfits()
      privateOutfits.value = privateRes || []
      const favoriteRes: any = await getFavoriteOutfits()
      favoriteOutfits.value = favoriteRes || []
      loadWardrobe() // 加载衣柜数据
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
  const isTargetPrivate = newStatus === 'PRIVATE'
  const actionText = isTargetPrivate ? '移入私人衣橱' : '发布到社区'
  
  try {
    await ElMessageBox.confirm(`确定要将该穿搭${actionText}吗？`, '状态流转', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
      customClass: 'glass-message-box'
    })
    
    await updateOutfitStatus(item.id, newStatus as any)
    ElMessage.success(`${actionText}成功`)
    
    // 本地模型状态更新，确保渲染一致
    item.status = newStatus
    
    if (isTargetPrivate) {
      // 从公开列表移除，加入私人列表
      const idx = outfits.value.findIndex(o => o.id === item.id)
      if (idx !== -1) outfits.value.splice(idx, 1)
      privateOutfits.value.unshift(item)
    } else {
      // 从私人列表移除，加入公开列表
      const idx = privateOutfits.value.findIndex(o => o.id === item.id)
      if (idx !== -1) privateOutfits.value.splice(idx, 1)
      outfits.value.unshift(item)
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

const handleLike = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
    return
  }
  try {
    if (item.liked) {
      await unlikeOutfit(item.id, currentUserId.value!)
      item.likeCount = Math.max(0, (item.likeCount || 0) - 1)
      item.liked = false
    } else {
      await likeOutfit(item.id, currentUserId.value!)
      item.likeCount = (item.likeCount || 0) + 1
      item.liked = true
      ElMessage({ message: '点赞成功！', type: 'success', grouping: true })
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleFavorite = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
    return
  }
  try {
    if (item.favorited) {
      await unfavoriteOutfit(item.id, currentUserId.value!)
      item.favCount = Math.max(0, (item.favCount || 0) - 1)
      item.favorited = false
      if (activeTab.value === 'favorites') {
        favoriteOutfits.value = favoriteOutfits.value.filter(o => o.id !== item.id)
      }
    } else {
      await favoriteOutfit(item.id, currentUserId.value!)
      item.favCount = (item.favCount || 0) + 1
      item.favorited = true
      ElMessage({ message: '收藏成功！', type: 'success', grouping: true })
      if (activeTab.value !== 'favorites') {
        const exist = favoriteOutfits.value.find(o => o.id === item.id)
        if (!exist) {
           favoriteOutfits.value.unshift({...item})
        }
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
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


onMounted(loadUser)
</script>

<template>
  <div class="min-h-screen bg-background pb-20">
    <!-- 个人资料英雄背景 -->
    <div class="relative h-64 overflow-hidden">
      <div class="absolute inset-0 bg-gradient-to-br from-primary/30 via-secondary/40 to-background animate-aurora"></div>
      <div class="absolute inset-0 backdrop-blur-[2px]"></div>
      <!-- 装饰性光晕 -->
      <div class="absolute -top-24 -left-24 w-96 h-96 bg-primary/20 rounded-full blur-[100px] animate-pulse"></div>
      <div class="absolute -bottom-24 -right-24 w-96 h-96 bg-secondary/20 rounded-full blur-[100px] animate-pulse" style="animation-delay: 2s"></div>
    </div>

    <!-- 个人资料卡片容器 -->
    <div class="max-w-5xl mx-auto px-6 -mt-24 relative z-10">
      <div class="glass-card p-8 md:p-10 rounded-[32px] border border-white/20 shadow-2xl backdrop-blur-xl animate-slide-up">
        <div class="flex flex-col md:flex-row items-center md:items-end gap-8">
          <!-- 头像区域 -->
          <div class="relative group">
            <div class="absolute -inset-1 bg-gradient-to-tr from-primary to-secondary rounded-full blur opacity-25 group-hover:opacity-50 transition duration-500"></div>
            <el-avatar 
              :size="140" 
              :src="user?.avatar" 
              class="relative border-4 border-white shadow-xl hover:scale-105 transition-transform duration-500 cursor-pointer"
            />
            <div v-if="isCurrentUser" class="absolute bottom-1 right-1 w-8 h-8 bg-primary rounded-full flex-center text-white border-2 border-white shadow-lg cursor-pointer hover:scale-110 transition-transform" @click="editDialogRef?.open(currentUserId)">
              <el-icon size="16"><MagicStick /></el-icon>
            </div>
          </div>

          <!-- 用户基本信息 -->
          <div class="flex-1 text-center md:text-left space-y-4">
            <div class="space-y-1">
              <h1 class="text-4xl font-bold tracking-tight text-foreground">
                {{ user?.nickname || user?.username }}
              </h1>
            </div>
            
            <p class="text-muted-foreground text-sm max-w-xl leading-relaxed italic">
              "{{ user?.bio || '这家伙很懒，什么都没留下~' }}"
            </p>

            <!-- 统计数据 -->
            <div class="flex gap-8 justify-center md:justify-start pt-2">
              <div class="stat-group">
                <span class="stat-value">{{ user?.followCount || 0 }}</span>
                <span class="stat-label">关注</span>
              </div>
              <div class="w-px h-8 bg-border/50 self-center"></div>
              <div class="stat-group">
                <span class="stat-value">{{ user?.fanCount || 0 }}</span>
                <span class="stat-label">粉丝</span>
              </div>
              <div class="w-px h-8 bg-border/50 self-center"></div>
              <div class="stat-group">
                <span class="stat-value">{{ outfits.length }}</span>
                <span class="stat-label">作品</span>
              </div>
            </div>
          </div>

          <!-- 交互操作按钮 -->
          <div class="flex gap-3 md:pb-2">
            <el-button 
              v-if="isCurrentUser" 
              type="primary" 
              round 
              size="large"
              class="premium-button px-8"
              @click="editDialogRef?.open(currentUserId)"
            >
              编辑资料
            </el-button>
            <el-button 
              v-if="!isCurrentUser" 
              :type="isFollowing ? 'default' : 'primary'" 
              round 
              size="large"
              class="px-8 shadow-lg hover:shadow-primary/20"
              @click="handleFollow"
            >
              {{ isFollowing ? '已关注' : '加关注' }}
            </el-button>
            <el-button v-if="!isCurrentUser" circle size="large" class="shadow-md">
              <el-icon><Message /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <div class="mt-8">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <el-tab-pane label="公开穿搭" name="public">
            <MasonryGallery :outfits="outfits" @delete="handleDelete" @toggle-status="(item) => handleToggleStatus(item, 'PRIVATE')" @like="handleLike" @favorite="handleFavorite" />
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="私人衣橱" name="private">
            <div class="pt-2">
              <el-alert title="这是您的私密空间，此页签下所有内容仅您本人可见。" type="warning" show-icon class="mb-6" :closable="false" />
              <MasonryGallery :outfits="privateOutfits" @delete="handleDelete" @toggle-status="(item) => handleToggleStatus(item, 'PUBLISHED')" @like="handleLike" @favorite="handleFavorite" />
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="我的收藏" name="favorites">
            <div class="pt-2">
              <MasonryGallery :outfits="favoriteOutfits" @delete="handleDelete" @like="handleLike" @favorite="handleFavorite" :hideManagementButtons="true" />
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="我的衣柜" name="wardrobe">
            <div class="pt-4" v-loading="isUploading || wardrobeLoading" element-loading-text="AI 正在努力分析中...">
              <div class="flex justify-between items-center mb-6">
                <div>
                  <h3 class="text-lg font-bold flex items-center gap-2">
                    <el-icon class="text-primary"><MagicStick /></el-icon>
                    我的电子衣橱
                    <span class="text-xs font-normal text-muted-foreground bg-secondary/50 px-2 py-0.5 rounded-full">
                      {{ wardrobeItems.length }} 件单品
                    </span>
                  </h3>
                </div>
                <!-- 隐藏的批量上传 input -->
                <input
                  type="file"
                  ref="fileInputRef"
                  multiple
                  accept="image/*"
                  class="hidden"
                  @change="handleWardrobeBatchUpload"
                />
                <el-button type="primary" :icon="Plus" round @click="() => fileInputRef?.click()">
                  批量上传单品
                </el-button>
              </div>

              <div v-if="wardrobeItems.length === 0" class="py-20 text-center bg-secondary/10 rounded-3xl border-2 border-dashed border-border/50">
                <el-empty description="衣柜里空空如也，快去上传您的时尚单品吧！" />
              </div>

              <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-6">
                <div v-for="item in wardrobeItems" :key="item.id" class="group relative bg-card rounded-2xl overflow-hidden border border-border/50 shadow-sm transition-all hover:shadow-lg">
                  <!-- 单品图片预览 -->
                  <div class="aspect-[3/4] overflow-hidden bg-secondary/20">
                    <el-image 
                      :src="item.originalImageUrl" 
                      class="w-full h-full object-cover transition-transform group-hover:scale-110"
                      :preview-src-list="[item.originalImageUrl]"
                      :preview-teleported="true"
                      hide-on-click-modal
                    />
                  </div>
                  
                  <!-- 单品基本信息 -->
                  <div class="p-3 bg-white/90 backdrop-blur-md absolute bottom-0 left-0 right-0 transform translate-y-full group-hover:translate-y-0 transition-transform duration-300">
                    <div class="flex justify-between items-center">
                      <div class="overflow-hidden">
                        <div class="text-xs font-bold text-primary truncate">
                          {{ item.categoryMain || '未分类' }} · {{ item.categorySub || '未知' }}
                        </div>
                        <div class="text-[10px] text-muted-foreground truncate mt-1 flex gap-2">
                          <span>🎨 {{ item.color || '未知' }}</span>
                          <span>📅 {{ item.season || '四季' }}</span>
                        </div>
                      </div>
                      <el-button 
                        type="danger" 
                        :icon="Delete" 
                        circle 
                        size="small" 
                        plain 
                        @click="handleWardrobeDelete(item.id)"
                        class="hover:scale-110 transition-transform"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <EditProfileDialog ref="editDialogRef" @success="handleEditSuccess" />
  </div>
</template>

<style scoped>
.profile-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.profile-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px;
  background: var(--el-color-primary);
}

.profile-tabs :deep(.el-tabs__item) {
  font-size: 1.1rem;
  font-weight: 600;
  padding: 0 2rem;
  transition: all 0.3s;
}

.profile-tabs :deep(.el-tabs__item.is-active) {
  color: var(--el-color-primary);
  transform: translateY(-2px);
}

.glass-card {
  background: rgba(255, 255, 255, 0.7);
}

.dark .glass-card {
  background: rgba(15, 23, 42, 0.7);
}

.stat-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform 0.3s;
}

@media (min-width: 768px) {
  .stat-group {
    align-items: flex-start;
  }
}

.stat-group:hover {
  transform: translateY(-2px);
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 800;
  background: var(--el-color-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.stat-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.premium-button {
  background: var(--el-color-primary);
  border: none;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.premium-button:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 10px 20px -10px var(--el-color-primary);
}

@keyframes aurora {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.animate-aurora {
  background-size: 200% 200%;
  animation: aurora 15s ease infinite;
}

@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-slide-up {
  animation: slide-up 0.8s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

/* 玻璃拟态适配 */
.glass-message-box {
  background: rgba(255, 255, 255, 0.8) !important;
  backdrop-filter: blur(20px) !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
  border-radius: 24px !important;
}
</style>
