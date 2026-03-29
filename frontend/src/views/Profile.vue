<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getUserInfo, followUser, unfollowUser } from '@/api/user'
import { getMyOutfits, getUserOutfits, deleteOutfit, getMyPrivateOutfits, updateOutfitStatus, getFavoriteOutfits } from '@/api/outfit'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '@/api/interaction'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { aiTryOn } from '@/api/ai'
import { getWardrobeList, uploadWardrobeItem, deleteWardrobeItem } from '@/api/wardrobe'
import UploadImage from '@/components/UploadImage.vue'

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
                <el-button type="primary" :icon="Plus" round @click="() => ( $refs.fileInputRef as HTMLInputElement ).click()">
                  批量上传单品
                </el-button>
              </div>

              <div v-if="wardrobeItems.length === 0" class="py-20 text-center bg-secondary/10 rounded-3xl border-2 border-dashed border-border/50">
                <el-empty description="衣柜里空空如也，快去上传您的时尚单品吧！" />
              </div>

              <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-6">
                <div v-for="item in wardrobeItems" :key="item.id" class="group relative bg-card rounded-2xl overflow-hidden border border-border/50 shadow-sm transition-all hover:shadow-lg hover:-translate-y-1">
                  <!-- 单品图片预览 -->
                  <div class="aspect-[3/4] overflow-hidden bg-secondary/20">
                    <el-image 
                      :src="item.originalImageUrl" 
                      class="w-full h-full object-cover transition-transform group-hover:scale-110"
                      :preview-src-list="[item.originalImageUrl]"
                      hide-on-click-modal
                    />
                  </div>
                  
                  <!-- 单品基本信息 -->
                  <div class="p-3 bg-white/90 backdrop-blur-md absolute bottom-0 left-0 right-0 transform translate-y-full group-hover:translate-y-0 transition-transform duration-300">
                    <div class="flex justify-between items-center">
                      <div class="overflow-hidden">
                        <div class="text-xs font-bold text-primary truncate">{{ item.categoryMain || '未分类' }}</div>
                        <div class="text-[10px] text-muted-foreground truncate">{{ item.season || '四季' }}</div>
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
